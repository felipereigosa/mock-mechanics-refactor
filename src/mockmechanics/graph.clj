(ns mockmechanics.graph
  (:require [mockmechanics.engine.util :refer :all]))

(defn- save-value [world [part-name function]]
  (let [value (get-in world [:parts part-name :value])]
    {part-name (assoc-in function [:value] value)}))

(defn- run-function [world graph-name part-name time dt]
  (let [graph (get-in world [:parts graph-name])
        function (get-in graph [:functions part-name])
        points (:points function)
        t1 (first (first points))
        t2 (first (last points))
        time (within time t1 t2)
        new-value (cond-> (get-function-value
                            points time interpolate-values)
                    (:relative function) (+ (:value function)))]
    (assoc-in world [:parts part-name :value] new-value)))

(defn- run-graph [world graph-name dt]
  (let [graph (get-in world [:parts graph-name])
        final-time (:final-time graph)
        time (:time graph)]
    (if (> time (+ final-time dt))
      (assoc-in world [:active-graphs graph-name] false)
      (let [world (update-in world [:parts graph-name :time] #(+ % dt))]
        (reduce (fn [w function-name]
                  (run-function w graph-name function-name time dt))
                world
                (keys (:functions graph)))))))

(defn run [world elapsed]
  (let [dt (/ elapsed 1000.0)
        graph-names (map-filter (:parts world)
                                #(= (:type %) :graph))]
    (reduce (fn [w graph-name]
              (run-graph w graph-name dt))
            world
            graph-names)))

(defn activate [world graph-name]
  (let [functions (->> (get-in world [:parts graph-name :functions])
                       (map-map #(save-value world %)))
        final-time (->> (vals functions)
                        (map (comp first last :points))
                        (apply max))]
    (-> world
        (assoc-in [:parts graph-name :functions] functions)
        (assoc-in [:parts graph-name :final-time] final-time)
        (assoc-in [:parts graph-name :time] 0.0)
        (assoc-in [:active-graphs graph-name] true))))

(defn any-active? [world]
  (some identity (vals (:active-graphs world))))
