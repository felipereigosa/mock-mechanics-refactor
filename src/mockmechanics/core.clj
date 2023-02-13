(ns mockmechanics.core
  (:gen-class)
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.camera :as camera]
            [mockmechanics.engine.picture :as picture]
            [mockmechanics.engine.window :as window]
            [mockmechanics.engine.graphics :as graphics]
            [mockmechanics.engine.transform :as transform]

            [mockmechanics.parts :as parts]
            [mockmechanics.mechanical-tree :as mechanical-tree]
            [mockmechanics.hints :as hints]
            [mockmechanics.input-indicator :as input-indicator]
            [mockmechanics.engine.keys :as keys]
            [mockmechanics.output :as output]
            [mockmechanics.engine.world :refer [world]]
            [mockmechanics.graph :as graph]
            [mockmechanics.motherboard :as motherboard]
            ))

(defn create-world []
  (-> (window/create-base-world)
      (assoc-in [:num-lines] 1)
      (assoc-in [:background-meshes :grid] (graphics/create-grid-mesh 24 0.5))
      (assoc-in [:action-menu]
                (picture/create "action-menu" 40 340 40 -1))

      (input-indicator/create 100)
      (assoc-in [:info] (parts/create-info))

      (assoc-in [:background-meshes :cube]
                (graphics/create-model-mesh
                  "res/cube.obj" [1 0.05 0] [1 0 0 0] 0.1 :red))

      (parts/create :ground :ground-part nil [0 -0.1 0] [1 0 0 0])

      (parts/create :track :track000 :ground-part [0 0.5 0] [1 0 0 0])
      (parts/create :track :track001 :track000 [1 1 0] [0 0 1 -90])
      (assoc-in [:parts :track001 :scale 1] 2)
      (parts/create :wagon :wagon002 :track000 [0 0 0] [1 0 0 0])

      (parts/create :graph :graph003 :ground-part [0 0.035 1] [0 0 1 0])
      (assoc-in [:parts :graph003 :functions]
                {:wagon002 {:points [[0 0] [2.0 3]]
                            :relative false}})

      (parts/create :graph :graph003b :ground-part [-1 0.035 1] [0 0 1 0])
      (assoc-in [:parts :graph003b :functions]
                {:wagon002 {:points [[0 3] [2.0 0]]
                            :relative false}})

      (parts/create :block :block005 :ground-part [2 0.25 1] [0 0 1 0])

      (parts/create :motherboard :motherboard006 :ground-part [1 0.035 1] [0 0 1 0])
      (update-in [:parts :motherboard006]
                 #(merge %
                         {:pins {:block005 {:x 10 :trigger true :value 0}
                                 :graph003 {:x 20 :trigger false :value 0}
                                 :graph003b {:x 30 :trigger false :value 0}}
                          :connections {:connection10353 {:points [:block005 :graph003b]
                                                          :tab 0}}}))

      (parts/create :probe :probe007 :wagon002 [0 0 0.125] [0 0 1 0])
      (parts/create :probe :probe008 :ground-part [2.1 1 0.125] [0 0 1 0])
      ))

(defn draw-3d! [world]
  (doseq [mesh (vals (:background-meshes world))]
    (graphics/draw-mesh! world mesh))

  (parts/draw! world (> (get-in world [:camera :x-angle]) 0)))

(defn draw-2d! [world]
  (graphics/clear!)

  (let [{:keys [image x y w h]} (:action-menu world)]
    (graphics/fill-rect! (make-color 70 70 70) x y (+ 20 w) (+ 20 h))
    (graphics/draw-image! image x y))

  (hints/draw! world)
  ;; (input-indicator/draw! world)
  (output/draw! world))

(def redraw-flag (atom true))

(defn draw-world! [world]
  (try
    (draw-3d! world)
    (catch Exception e))

  (when @redraw-flag
    (try
      (draw-2d! world)
      (catch Exception e))
    (reset! redraw-flag false))

  (graphics/clear-depth-buffer)
  (graphics/draw-ortho-mesh! world (:ortho-mesh world)))

(defn update-world [world elapsed]
  (let [world (if (graph/any-active? world)
                (window/redraw world)
                world)]
    (-> world
        (update-in [:background-meshes :cube :color]
                   (fn [color]
                     (if (= color [1 0 0 1])
                       [1 1 0 1]
                       [1 0 0 1])))
        parts/set-probe-values
        (graph/run elapsed)
        mechanical-tree/compute-transforms
        motherboard/update
        parts/value-changed)))

(defn key-pressed [world event]
  (let [world (input-indicator/key-pressed world event)]
    (let [key-name (get-in keys/keymap [(:code event)])]
      (cond
        (= key-name :control) (assoc-in world [:control-pressed] true)
        (= key-name :shift) (assoc-in world [:shift-pressed] true)
        (= key-name :alt) (assoc-in world [:alt-pressed] true)
        (= key-name :enter)
        (let [w (create-world)]
          (reset! redraw-flag true)
          (println "recreate world")
          w)
        :else world))))

(defn key-released [world event]
  (let [world (input-indicator/key-released world event)]
    (let [key-name (get-in keys/keymap [(:code event)])]
      (cond
        (= key-name :control) (assoc-in world [:control-pressed] false)
        (= key-name :shift) (assoc-in world [:shift-pressed] false)
        (= key-name :alt) (assoc-in world [:alt-pressed] false)
        :else world))))

(defn mouse-pressed [world {:keys [x y button] :as event}]
  (let [world (input-indicator/mouse-pressed world event)]
    (cond-> world
        true (assoc-in [:show-hints] true)
        (and (< x 100) (< y 100)) (hints/show :action :new)
        (= button :right) (assoc-in [:last-point] [x y]))))

(defn mouse-moved [world event]
  (if (:shift-pressed world)
    (camera/mouse-pan world event)
    (camera/mouse-rotate world event)))

(defn mouse-released [world event]
  (let [world (-> world
                  (input-indicator/mouse-released event)
                  (dissoc-in [:last-point]))]
    (reset! redraw-flag true)
    world))

(defn mouse-scrolled [world event]
  (let [world (input-indicator/mouse-scrolled world event)
        amount (+ 1 (* (:amount event) -0.05))]
    (camera/zoom world amount)))

(defn window-changed [world event]
  (window/recompute-viewport world))

(defn -main [& args]
  (.start (Thread.
            (fn []
              (window/create
                {:create #(create-world)
                 :draw #(draw-world! %)
                 :update #(update-world %1 %2)
                 :key-pressed #(key-pressed %1 %2)
                 :key-released #(key-released %1 %2)
                 :mouse-pressed #(mouse-pressed %1 %2)
                 :mouse-moved #(mouse-moved %1 %2)
                 :mouse-released #(mouse-released %1 %2)
                 :mouse-scrolled #(mouse-scrolled %1 %2)
                 :window-changed #(window-changed %1 %2)})))))
