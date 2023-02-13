(ns mockmechanics.hints
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.window :as window]
            [mockmechanics.engine.graphics :as graphics]
            [clojure.string :refer [split]]))

(def texts {:action {:new "Ctrl + N,New File"
                     :view "Ctrl + C,Center Camera"
                     :save "Ctrl + S,Save"
                     :open "Ctrl + O,Open"
                     :undo "Ctrl + Z,Undo"
                     :redo "Ctrl + R,Redo"
                     :cancel "Esc,Cancel Action"}
            :mode {:add "Alt + A,Add Mode"
                   :edit "Alt + E,Edit Mode"
                   :graph "Alt + G,Graph Mode"
                   :motherboard "Alt + M,Motherboard Mode"
                   :color "Alt + C,Color Mode"
                   :property "Alt + P,Property Mode"
                   :layer "Alt + L,Layer Mode"
                   :avatar "Alt + F,Avatar Mode"
                   :simulation "Alt + S,Simulation Mode"}
            :add {:block "B,Block"
                  :cylinder "C,Cylinder"
                  :cone "Shift + C,Cone"
                  :sphere "S,Sphere"
                  :wagon "W,Wagon"
                  :track "T,Track"
                  :chip "G,Graph Chip"
                  :motherboard "M,Motherboard"
                  :probe "P,Probe"
                  :button "Shift + B,Button"
                  :lamp "L,Lamp"
                  :speaker "Shift + S,Speaker"
                  :gear "Shift + G,Gear"
                  :display "I,Image"
                  :cable "R,Rope"}
            :edit {:move "M,Move"
                   :sink "H,Change Height"
                   :rotate "R,Rotate"
                   :delete "D,Delete"
                   :scale "S,Scale"
                   :copy "C,Copy/Paste"
                   :translate "T,Transfer"}
            :graph {:move "M,Move"
                    :set-x "X,Set x"
                    :set-y "Y,Set y"
                    :add "A,Add Node"
                    :delete "D,Delete Node"
                    :run "R,Run"
                    :toggle-relative "T,Toggle Relative"
                    :view "V,View"
                    :print-lengths "L,Print Lenghts"}
            :motherboard {:move "M,Move"
                          :and "A,And Gate"
                          :or "O,Or Gate"
                          :not "N,Not Gate"
                          :delete "D,Delete"
                          :connect "C,Connect"
                          :toggle "T, Toggle Trigger"
                          :run "R,Run"
                          :script "S,Toggle Script"
                          :editor "E,Open editor"}})

(defn show [world menu action]
  (if (:show-hints world)
    (do
      (window/do-later! #(dissoc-in % [:hint]) 1000)
      (assoc-in world [:hint] (get-in texts [menu action])))
    world))

(defn draw! [world]
  (if-let [hint (:hint world)]
    (let [[ww wh] (window/get-dimensions)
          x (/ ww 2)
          y (- (/ wh 2) 50)]
      (graphics/fill-rect! :black x y 500 150)
      (if (= (.indexOf hint ",") -1)
        (let [box {:x x :y y :w 500 :h 150}]
          (graphics/draw-text-in-box! hint :white 20 box))
        (let [[command description] (split hint #",")
              left-box {:x (- x 125) :y y :w 250 :h 150}
              right-box {:x (+ x 125) :y y :w 250 :h 150}]
          (graphics/draw-line! :white x (- y 50) x (+ y 50))
          (graphics/draw-text-in-box! command :white  20 left-box)
          (graphics/draw-text-in-box! description :red  20 right-box))))))
