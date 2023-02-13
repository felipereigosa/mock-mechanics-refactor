(ns mockmechanics.motherboard
  (:require [mockmechanics.engine.util :refer :all])
  (:refer-clojure :exclude [update]))

(load "script")

(defn get-inputs [world motherboard element-name]
  (let [connections (filter (fn [[name connection]]
                              (= (last (:points connection))
                                 element-name))
                            (:connections motherboard))]
    (map (comp first :points second) connections)))

(defn get-pin-direction [motherboard pin-name]
  (let [points (map :points (vals (:connections motherboard)))]
    (cond
      (in? pin-name (map first points)) :input
      (in? pin-name (map last points)) :output
      :else nil)))

(declare get-element-value)

(defn get-pin-value [world motherboard pin-name]
  (let [part (get-in world [:parts pin-name])
        direction (get-pin-direction motherboard pin-name)]
    (if (= direction :output)
      (->> pin-name
           (get-inputs world motherboard)
           first
           (get-element-value world motherboard))
      (get-in world [:parts pin-name :value]))))

(defn get-and-value [world motherboard gate-name]
  (let [inputs (get-inputs world motherboard gate-name)
        values (map #(get-element-value world motherboard %) inputs)]
    (if (every? #(float= % 1.0) values)
      1
      0)))

(defn get-or-value [world motherboard gate-name]
  (let [inputs (get-inputs world motherboard gate-name)
        values (map #(get-element-value world motherboard %) inputs)]
    (if (some #(float= % 1.0) values)
      1
      0)))

(defn get-not-value [world motherboard gate-name]
  (->> gate-name
       (get-inputs world motherboard)
       first
       (get-element-value world motherboard)
       (- 1)))

(defn get-gate-value [world motherboard gate-name]
  (let [gate (get-in motherboard [:gates gate-name])]
    (case (:type gate)
      :not (get-not-value world motherboard gate-name)
      :and (get-and-value world motherboard gate-name)
      :or (get-or-value world motherboard gate-name))))

(defn get-element-value [world motherboard element-name]
  (if (in? element-name (keys (:pins motherboard)))
    (get-pin-value world motherboard element-name)
    (get-gate-value world motherboard element-name)))

(defn run-graph [world motherboard-name]
  (let [motherboard (get-in world [:parts motherboard-name])
        output-names (-> #(= (get-pin-direction motherboard %) :output)
                         (filter (keys (:pins motherboard))))]
    (reduce (fn [w part-name]
              (let [value (get-element-value w motherboard part-name)]
                (assoc-in world [:parts part-name :value] value)))
            world
            output-names)))

(defn pin-value-changed [world motherboard-name]
  (if (get-in world [:parts motherboard-name :use-script])
    (run-script world motherboard-name)
    (run-graph world motherboard-name)))

(defn update-motherboard [world motherboard-name]
  (let [motherboard (get-in world [:parts motherboard-name])]
    (reduce (fn [w [pin-name pin]]
              (if (:trigger pin)
                (let [old-value (:value pin)
                      new-value (get-in world [:parts pin-name :value])]
                  (if (= new-value old-value)
                    w
                    (-> w
                        (pin-value-changed motherboard-name)
                        (assoc-in [:parts motherboard-name
                                   :pins pin-name :value] new-value))))
                w))
            world (:pins motherboard))))

(defn update [world]
  (reduce (fn [w motherboard-name]
            (update-motherboard w motherboard-name))
          world
          (map-filter (:parts world)
                      #(= (:type %) :motherboard))))
