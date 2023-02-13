(ns mockmechanics.engine.camera
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.matrix :as matrix]
            [mockmechanics.engine.analytic-geometry :as ag]))

(defn compute [world]
  (let [camera (:camera world)
        pivot (:pivot camera)
        eye (-> (:vector camera)
                (vector/multiply (:distance camera))
                (vector/rotate [1 0 0] (- (:x-angle camera)))
                (vector/rotate [0 1 0] (- (:y-angle camera)))
                (vector/add pivot))]
    (-> world
        (assoc-in [:view-matrix] (matrix/get-look-at eye pivot [0 1 0]))
        (assoc-in [:camera :eye] eye))))

(defn create [world vector distance x-angle y-angle]
  (-> world
      (assoc-in [:camera] {:vector vector
                           :distance distance
                           :x-angle x-angle
                           :y-angle y-angle
                           :pivot [0 0 0]})
      compute))

(defn project-point [world point]
  ;; (let [p-matrix (:projection-matrix world)
  ;;       v-matrix (:view-matrix world)
  ;;       matrix (matrix/multiply v-matrix p-matrix)
  ;;       point (float-array (conj point 1.0))
  ;;       point-2d (vec (matrix/multiply-vector matrix point))
  ;;       [x y _ _] (map #(/ % (nth point-2d 3)) point-2d)
  ;;       [ww wh] (window/get-dimensions)]
  ;;   [(int (* (/ (inc x) 2) ww))
  ;;    (int (* (/ (inc (- y)) 2) wh))])
  [0 0]
  )

(defn unproject-point [world [x y]]
  ;; (let [[ww wh] (window/get-dimensions)
  ;;       dx (dec (/ x (/ ww 2)))
  ;;       dy (- (dec (/ y (/ wh 2))))
  ;;       p-matrix (:projection-matrix world)
  ;;       v-matrix (:view-matrix world)
  ;;       matrix (matrix/multiply v-matrix p-matrix)
  ;;       inverse-matrix (matrix/get-inverse matrix)
  ;;       p-2d-a (float-array [dx dy -1.0 1.0])
  ;;       p-3d-a (vec (matrix/multiply-vector inverse-matrix p-2d-a))
  ;;       p-3d-a (map #(/ % (nth p-3d-a 3)) p-3d-a)
  ;;       p-3d-a (vec (butlast p-3d-a))

  ;;       p-2d-b (float-array [dx dy 0.0 1.0])
  ;;       p-3d-b (vec (matrix/multiply-vector inverse-matrix p-2d-b))
  ;;       p-3d-b (map #(/ % (nth p-3d-b 3)) p-3d-b)
  ;;       p-3d-b (vec (butlast p-3d-b))]
  ;;   [p-3d-a (vector/normalize (vector/subtract p-3d-b p-3d-a))])
  [[0 0 0] [0 1 0]]
  )

(defn rotate [world dx dy]
  (let [x-speed 0.4
        y-speed 0.4
        camera (-> (:camera world)
                   (update-in [:x-angle]
                              (fn [angle]
                                (within (+ angle (* dy y-speed)) -89 89)))
                   (update-in [:y-angle] (fn [angle] (+ angle (* dx y-speed)))))]
    (-> world
        (assoc-in [:camera] camera)
        compute)))

(defn pan [world x1 y1 x2 y2]
  (let [l1 (unproject-point world [x1 y1])
        l2 (unproject-point world [x2 y2])
        plane [[0 0 0] [0 0 1] [1 0 0]]
        p1 (ag/line-plane-intersection l1 plane)
        p2 (ag/line-plane-intersection l2 plane)
        d (vector/subtract p1 p2)]
    (-> world
        (update-in [:camera :pivot] (fn [pivot]
                                      (vector/add pivot d)))
        compute)))

(defn get-plane [world point]
  (let [camera (:camera world)
        to-camera (vector/subtract (:eye camera) (:pivot camera))
        x-axis (vector/cross-product [0 1 0] to-camera)
        y-axis (vector/cross-product x-axis to-camera)
        p1 point
        p2 (vector/add point x-axis)
        p3 (vector/add point y-axis)]
    [p1 p2 p3]))

(defn zoom [world amount]
  (-> world
      (update-in [:camera :distance] #(* % amount))
      compute))

(defn mouse-rotate [world event]
  (let [[x y] (:last-point world)
        dx (- (:x event) x)
        dy (- (:y event) y)]
    (-> world
        (rotate dx dy)
        (assoc-in [:last-point] [(:x event) (:y event)]))))

(defn mouse-pan [world event]
  (let [[x1 y1] (:last-point world)
        x2 (:x event)
        y2 (:y event)]
    (-> world
        (pan x1 y1 x2 y2)
        (assoc-in [:last-point] [x2 y2]))))

(defn reset [world]
  (create world [0 0 1] 40 25 -35))
