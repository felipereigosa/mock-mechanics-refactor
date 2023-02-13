(ns mockmechanics.output
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.graphics :as graphics]
            [mockmechanics.engine.window :as window]
            [clojure.string :refer [split]]))

(def output (atom ""))

(defn draw-mode-text! [world]
  (let [text (str (kw->str (:mode world)) " mode")
        size 15
        width (graphics/get-text-width! text size)
        [ww wh] (window/get-dimensions)
        x (- ww width 10)
        y (- wh 6)]
    (graphics/draw-text! :gray text x y size)))

(defn draw! [world]
  (let [[ww wh] (window/get-dimensions)
        hww (* ww 0.5)
        hwh (* wh 0.5)
        num-lines (:num-lines world)
        height (+ (* num-lines 16) 10)
        hh (* height 0.5)
        helper (fn [lines color background-color marker]
                 (graphics/fill-rect! background-color
                                      hww (- wh hh) ww height)
                 (dotimes [i (count lines)]
                   (let [text (nth lines i)
                         text (if marker
                                (str "= " text)
                                text)
                         y (+ (* i 16) (- wh height) 18)]
                     (graphics/draw-text! color text 30 y 14))))]
    (cond
      (:text-input world)
      (helper [(str (:text world))] :red :white true)

      (not (empty? (:command world)))
      (helper [(:command world)] :blue :black false)

      :else
      (let [lines (take-last num-lines (split @output #"\n"))]
        (helper lines  :green :black false)))

    (draw-mode-text! world)))

(defn print! [& args]
  (let [line (apply print-str (conj (vec args) "\n"))
        truncated-output (apply str (take-last 1024 @output))]
    (reset! output (str truncated-output line))
    nil))

(defn clear! []
  (reset! output "")
  nil)
