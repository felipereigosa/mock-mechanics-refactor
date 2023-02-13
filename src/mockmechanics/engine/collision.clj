(ns mockmechanics.engine.collision
  (:require [mockmechanics.engine.matrix :as matrix]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.transform :as transform]
            [mockmechanics.engine.analytic-geometry :as ag]))

;; (defn distance-comparator [a b]
;;   (cond
;;     (nil? a) false
;;     (nil? b) true
;;     (and (nil? a) (nil? b)) a
;;     :else (< a b)))

;; (defn get-mesh-triangles [mesh scale]
;;   (let [vertices (partition 3 (vec (:vertices mesh)))
;;         matrix (matrix/multiply
;;                  (apply matrix/get-scale scale)
;;                  (transform/get-matrix mesh))
;;         vertices (map (fn [[x y z]]
;;                         (let [vertex (float-array [x y z 1])]
;;                           (butlast (vec (matrix/multiply-vector
;;                                           matrix vertex)))))
;;                       vertices)]
;;     (partition 3 vertices)))

;; (defn get-mesh-collision [mesh scale line]
;;   (let [triangles (get-mesh-triangles mesh scale)
;;         measured-triangles (map (fn [i]
;;                                   {:d (ag/line-triangle-distance
;;                                         line (nth triangles i))
;;                                    :i i})
;;                                 (range (count triangles)))
;;         collision (first (sort-by :d distance-comparator measured-triangles))]
;;     (if (nil? (:d collision))
;;       nil
;;       [(:i collision) (:d collision) (ag/line-get-point line (:d collision))])))

;; (defn get-collision-model [world type]
;;   (or (get-in world [:info type :collision-model])
;;       (get-in world [:info :block :model])))

;; (defn get-collision-normal [world collision]
;;   (let [{:keys [part-name point index]} collision
;;         type (get-in world [:parts part-name :type])
;;         mesh (get-collision-model world type)
;;         triangles (partition 3 (partition 3 (:vertices mesh)))
;;         [a b c] (nth triangles index)
;;         v1 (vector/subtract b a)
;;         v2 (vector/subtract c a)]
;;     (vector/cross-product v1 v2)))
