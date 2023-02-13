(ns mockmechanics.engine.transform
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.matrix :as matrix])
  (:refer-clojure :exclude [apply remove])
  (:import (javax.vecmath Matrix4f Quat4f AxisAngle4f)
           com.bulletphysics.linearmath.Transform))

(def t (new Transform))
(def q (new Quat4f))
(def a (new AxisAngle4f))
(def m (new Matrix4f))

(defn get-matrix [[position rotation]]
  (let [matrix (float-array (range 16))
        [x y z] position
        [ax ay az angle] rotation]
    (.set (.origin t) x y z)
    (.set a ax ay az (to-radians angle))
    (.set q a)
    (.setRotation t q)
    (.getOpenGLMatrix t matrix)
    matrix))

(defn from-matrix [matrix]
  (.setFromOpenGLMatrix t matrix)
  (let [vec (.-origin t)]
    (.getRotation t q)
    (.set a q)
    [[(.-x vec) (.-y vec) (.-z vec)]
     [(.-x a) (.-y a) (.-z a)
      (to-degrees (.-angle a))]]))

(defn combine [a b]
  (let [ma (get-matrix a)
        mb (get-matrix b)
        m (matrix/multiply ma mb)]
    (from-matrix m)))

(defn remove [a b]
  (let [ma (get-matrix a)
        mb (get-matrix b)
        imb (matrix/get-inverse (float-array mb))
        m (matrix/multiply ma imb)]
    (from-matrix m)))

(defn apply [transform point]
  (let [matrix (get-matrix transform)
        vector (float-array (conj (vec point) 1))]
    (vec (butlast (matrix/multiply-vector matrix vector)))))

(defn get-inverse [transform]
  (let [m (get-matrix transform)
        im (matrix/get-inverse m)]
    (from-matrix im)))

(defn interpolate [t1 t2 s]
  (let [[p1 r1] t1
        [p2 r2] t2
        p (vector/interpolate p1 p2 s)
        angle (interpolate-values (last r1) (last r2) s)
        r (conj (vec (butlast r1)) angle)]
    [p r]))

(defn extract [m]
  [(:position m) (:rotation m)])
