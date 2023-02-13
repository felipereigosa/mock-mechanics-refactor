(ns mockmechanics.track-path
  (:refer-clojure :exclude [set])
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.transform :as transform]))

(defn- get-next-track [world track-name direction]
  (let [track (get-in world [:parts track-name])
        get-type #(get-in world [:parts % :type])
        next-name (if (= direction :before)
                    (:parent track)
                    (find-if #(= (get-type %) :track)
                             (keys (:children track))))
        next-part (get-in world [:parts next-name])]
    (if (and (= (:type next-part) :track)
             (= (:color next-part) (:color track)))
      next-name
      nil)))

(defn- grow-path [world tracks]
  (let [before (get-next-track world (first tracks) :before)
        after (get-next-track world (last tracks) :after)]
    (cond
      before (recur world (vec (concat [before] tracks)))
      after (recur world (conj tracks after))
      :else tracks)))

(defn- get-track-point [world track-name offset]
  (let [track (get-in world [:parts track-name])
        point [0 (* (second (:scale track)) offset) 0]]
    (transform/apply (transform/extract track) point)))

(defn- get-track-path [world track-name]
  (let [track-names (grow-path world [track-name])
        points (mapv #(get-track-point world % -0.5)
                     track-names)
        points (conj points (get-track-point
                              world (last track-names) 0.5))
        track (get-in world [:parts track-name])
        transform (transform/extract track)
        inverse-transform (transform/get-inverse transform)]
    (mapv #(transform/apply inverse-transform %) points)))

(defn set [world wagon-name]
  (let [wagon (get-in world [:parts wagon-name])
        path (get-track-path world (:parent wagon))
        lengths (map (fn [a b]
                       (vector/length (vector/subtract a b)))
                     path (rest path))
        total-length (reduce + lengths)
        path-fn (map vector (accumulate lengths) path)]
    (assoc-in world [:parts wagon-name :path-fn] path-fn)))
