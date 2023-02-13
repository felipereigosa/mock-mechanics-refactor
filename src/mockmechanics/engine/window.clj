(ns mockmechanics.engine.window
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.matrix :as matrix]
            [mockmechanics.engine.camera :as camera]
            [mockmechanics.engine.world :refer [world]]
            [mockmechanics.engine.graphics :as graphics])
  (:import (org.lwjgl.glfw GLFW
                           GLFWKeyCallback
                           GLFWMouseButtonCallback
                           GLFWCursorPosCallback
                           GLFWScrollCallback
                           GLFWWindowSizeCallback
                           GLFWWindowFocusCallback)
           (org.lwjgl.system MemoryUtil MemoryStack)
           (org.lwjgl.opengl GL GL11)))

(def time-since-update (atom 0))
(def window (atom nil))

(defn create-key-handler! [key-pressed-fn key-released-fn]
  (GLFW/glfwSetKeyCallback
    @window
    (proxy [GLFWKeyCallback] []
      (invoke [_ key scancode action mods]
        (cond
          (= action GLFW/GLFW_PRESS)
          (try
            (swap! world #(key-pressed-fn % {:code key}))
            (reset! time-since-update 0)
            (catch Exception e))

          (= action GLFW/GLFW_RELEASE)
          (try
            (swap! world #(key-released-fn % {:code key}))
            (reset! time-since-update 0)
            (catch Exception e)))))))

(def event (atom {}))

(defn create-mouse-handler! [mouse-pressed-fn mouse-released-fn]
  (GLFW/glfwSetMouseButtonCallback
    @window
    (proxy [GLFWMouseButtonCallback] []
      (invoke [_ button action mods]
        (swap! event assoc-in [:button] (get {0 :left
                                         1 :right
                                         2 :middle}
                                        button))
        (cond
          (= action GLFW/GLFW_PRESS)
          (try

            (swap! world #(mouse-pressed-fn % @event))
            (reset! time-since-update 0)
            (catch Exception e))

          (= action GLFW/GLFW_RELEASE)
          (try
            (swap! world #(mouse-released-fn % @event))
            (reset! time-since-update 0)
            (catch Exception e)))))))

(defn create-mouse-motion-handler! [mouse-moved-fn]
  (GLFW/glfwSetCursorPosCallback
    @window
    (proxy [GLFWCursorPosCallback] []
      (invoke [_ x y]
        (swap! event merge {:x x :y y})
        (try
          (swap! world #(mouse-moved-fn % @event))
          (when (:button @event)
            (reset! time-since-update 0))
          (catch Exception e))))))

(defn create-mouse-scroll-handler! [mouse-scrolled-fn]
  (GLFW/glfwSetScrollCallback
    @window
    (proxy [GLFWScrollCallback] []
      (invoke [_ x y]
        (try
          (swap! world
                 (fn [w]
                   (mouse-scrolled-fn
                     w (assoc-in @event [:amount] y))))
          (reset! time-since-update 0)
          (catch Exception e))))))

(defn create-window-size-handler! [window-changed-fn]
  (GLFW/glfwSetWindowSizeCallback
    @window
    (proxy [GLFWWindowSizeCallback] []
      (invoke [_ width height]
        (try
          (swap! world
                 (fn [w]
                   (if (empty? w)
                     w
                     (window-changed-fn w {:width width
                                           :height height}))))
          (reset! time-since-update 0)
          (catch Exception e))))))

(defn create-window-focus-handler! [window-focused-fn]
  (GLFW/glfwSetWindowFocusCallback
    @window
    (proxy [GLFWWindowFocusCallback] []
      (invoke [_ focused]
        (try
          (swap! world
                 (fn [w]
                   (window-focused-fn w focused)))
          (catch Exception e))))))

(def last-time (atom (get-current-time)))

(defn update-and-draw! [draw-fn update-fn]
  (if (< @time-since-update 200)
    (let [current-time (get-current-time)
          elapsed (within (- current-time @last-time) 0 40)]
      (reset! last-time current-time)
      (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT
                            GL11/GL_DEPTH_BUFFER_BIT))
      (try
        (draw-fn @world)
        (catch Exception e))
      (try
        (swap! world (fn [w] (update-fn w elapsed)))
        (catch Exception e))

      (GLFW/glfwSwapBuffers @window)
      (swap! time-since-update #(+ elapsed %)))
    (sleep 5))

  (GLFW/glfwPollEvents))

(defn create [handlers]
  (GLFW/glfwInit)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
  (GLFW/glfwWindowHint GLFW/GLFW_SAMPLES 8)
  (GLFW/glfwWindowHint GLFW/GLFW_MAXIMIZED GLFW/GLFW_FALSE)

  (let [width 800
        height 600
        get-handler #(or (get handlers %) (fn [world _] world))]
    (reset! window (GLFW/glfwCreateWindow
                     width height "-"
                     MemoryUtil/NULL MemoryUtil/NULL))
    (create-key-handler! (get-handler :key-pressed)
                         (get-handler :key-released))
    (create-mouse-handler! (get-handler :mouse-pressed)
                           (get-handler :mouse-released))
    (create-mouse-motion-handler! (get-handler :mouse-moved))
    (create-mouse-scroll-handler! (get-handler :mouse-scrolled))
    (create-window-size-handler! (get-handler :window-changed))
    (create-window-focus-handler! (get-handler :window-focused))

    (GLFW/glfwMakeContextCurrent @window)
    (GLFW/glfwSwapInterval 1)
    (GLFW/glfwShowWindow @window)

    (GL/createCapabilities)

    (GL11/glViewport 0 0 width height)
    (GL11/glClearColor 0.0 0.0 0.0 0.0)

    (GL11/glEnable GL11/GL_BLEND)
    (GL11/glBlendFunc GL11/GL_SRC_ALPHA GL11/GL_ONE_MINUS_SRC_ALPHA)

    (GL11/glEnable GL11/GL_DEPTH_TEST)

    (reset! world ((:create handlers)))
    (reset! time-since-update 0)

    (while (not (GLFW/glfwWindowShouldClose @window))
      (update-and-draw! (:draw handlers) (:update handlers)))

    (GLFW/glfwDestroyWindow @window)
    (GLFW/glfwTerminate)))

(defn get-dimensions []
  (let [stack (MemoryStack/stackPush)
        w (.mallocInt stack 1)
        h (.mallocInt stack 1)]
    (GLFW/glfwGetWindowSize @window w h)
    [(.get w 0) (.get h 0)]))

(defn set-title! [text]
  (GLFW/glfwSetWindowTitle @window text))

(defn redraw [world]
  (reset! time-since-update 0)
  world)

(defn do-later! [func time]
  (.start
    (new Thread
         (proxy [Runnable] []
           (run []
             (try
               (sleep time)
               (reset! world (-> @world
                                 func
                                 redraw))
               (catch Exception e)))))))

(defn recompute-viewport [world]
  (let [[width height] (get-dimensions)
        projection-matrix (matrix/get-perspective
                            10 (/ width height) 3 1000)]
    (GL11/glViewport 0 0 width height)
    (-> world
        (assoc-in [:projection-matrix] projection-matrix)
        (assoc-in [:ortho-mesh]
                  (graphics/create-ortho-mesh width height)))))

(defn create-base-world []
  (GL/createCapabilities)
  (GL11/glClearColor 0 0.5 0.8 0)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace GL11/GL_BACK)

  (-> {}
      (assoc-in [:programs :basic] (graphics/create-program "basic"))
      (assoc-in [:programs :flat] (graphics/create-program "flat"))
      (assoc-in [:programs :textured] (graphics/create-program "textured"))
      (assoc-in [:programs :ortho] (graphics/create-program "ortho"))
      (assoc-in [:programs :colored] (graphics/create-program "colored"))
      (assoc-in [:programs :animated] (graphics/create-program "animated"))
      recompute-viewport
      (camera/create [0 0 1] 40 25 -35)))
