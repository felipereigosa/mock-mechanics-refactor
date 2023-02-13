(ns mockmechanics.mechanical-tree
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.transform :as transform]))

(declare compute-subtree-transforms)

(defn- get-track-transform [world part-name transform]
  (let [track (get-in world [:parts part-name])
        angle (map-between-ranges (:value track) 0.0 1.0 0.0 360.0)
        angle-transform [[0 0 0] [0 1 0 angle]]]
    (transform/combine angle-transform transform)))

(defn- get-wagon-transform [world part-name transform]
  (let [wagon (get-in world [:parts part-name])
        rotation (second transform)
        path-fn (map (fn [[t v]]
                       [t (transform/apply transform v)])
                     (:path-fn wagon))
        position (get-function-value path-fn (:value wagon)
                                     vector/interpolate)]
    [position rotation]))

(defn- compute-children-transforms [world part-name transform]
  (reduce (fn [w [child-name relative-transform]]
            (let [new-transform (transform/combine
                                  relative-transform transform)]
              (compute-subtree-transforms
                w child-name new-transform)))
          world
          (get-in world [:parts part-name :children])))

(defn- compute-subtree-transforms [world part-name transform]
  (let [value-transform
        (case (get-in world [:parts part-name :type])
          :track (get-track-transform world part-name transform)
          :wagon (get-wagon-transform world part-name transform)
          transform)
        [position rotation] value-transform]
    (-> world
        (assoc-in [:parts part-name :position] position)
        (assoc-in [:parts part-name :rotation] rotation)
        (compute-children-transforms part-name value-transform))))

(defn compute-transforms [world]
  (let [ground (get-in world [:parts :ground-part])
        transform (transform/extract ground)]
    (compute-subtree-transforms world :ground-part transform)))
