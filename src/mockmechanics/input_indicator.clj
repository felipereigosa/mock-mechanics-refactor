(ns mockmechanics.input-indicator
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.picture :as picture]
            [mockmechanics.engine.graphics :as graphics]
            [mockmechanics.engine.window :as window]
            [mockmechanics.engine.keys :as keys]))

(defn create [world y]
  (let [x 105
        w 200]
    (assoc-in world [:input-indicator]
              {:picture (picture/create "indicator" x y w -1)
               :left (picture/create "left-button" x y w -1)
               :middle (picture/create "middle-button" x y w -1)
               :right (picture/create "right-button" x y w -1)
               :up (picture/create "up-button" x y w -1)
               :down (picture/create "down-button" x y w -1)
               :text ""
               :button nil})))

(defn draw! [world]
  (if-let [input-indicator (:input-indicator world)]
    (let [picture (:picture input-indicator)
          {:keys [image x y]} picture
          text-region (picture/get-absolute-region
                        (get-in picture [:regions :text]) picture)]
      (graphics/draw-image! image x y)
      (if-let [button (:button input-indicator)]
        (graphics/draw-image! (:image (get input-indicator button)) x y))
      (graphics/draw-text-in-box! (:text input-indicator) :white 20 text-region))))

(defn set-text [world text]
  (window/do-later! #(assoc-in % [:input-indicator :text] "") 1000)
  (assoc-in world [:input-indicator :text] text))

(defn key-pressed [world event]
  (if-let [input-indicator (:input-indicator world)]
    (let [key-name (get-in keys/keymap [(:code event)])]
      (cond
        (= key-name :control) (assoc-in world [:input-indicator :text] "Ctrl")
        (= key-name :shift) (assoc-in world [:input-indicator :text] "Shift")
        (= key-name :alt) (assoc-in world [:input-indicator :text] "Alt")
        (= key-name :esc) (set-text world "Esc")

        :else
        (if-let [key (keys/get (:code event)
                               (:control-pressed world)
                               (:alt-pressed world)
                               (:shift-pressed world))]
          (if (not (:text-input world))
            (set-text world (keys/pretty key))
            world)
          world)))
    world))

(defn key-released [world event]
  (if-let [{:keys [text]} (:input-indicator world)]
    (if (in? text ["Ctrl" "Shift" "Alt"])
      (assoc-in world [:input-indicator :text] "")
      world)
    world))

(defn mouse-scrolled [world event]
  (if-let [input-indicator (:input-indicator world)]
    (do
      (window/do-later! #(assoc-in % [:input-indicator :button] nil) 300)
      (assoc-in world [:input-indicator :button]
                (if (pos? (:amount event)) :up :down)))
    world))

(defn mouse-pressed [world event]
  (if-let [input-indicator (:input-indicator world)]
    (assoc-in world [:input-indicator :button] (:button event))
    world))

(defn mouse-released [world event]
  (if-let [input-indicator (:input-indicator world)]
    (dissoc-in world [:input-indicator :button])
    world))
