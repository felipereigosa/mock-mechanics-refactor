(defn get-spec-line [world spec]
  (or (:line spec)
      (unproject-point world [(:x spec) (:y spec)])))

(declare get-replaced-cable-parts)

(defn get-part-collision [world spec]
  (let [line (get-spec-line world spec)
        parts (get-replaced-cable-parts world)
        distances (map (fn [[part-name part]]
                         (if (not (in? (:layer part)
                                       (:visible-layers world)))
                           nil
                           (let [type (:type part)
                                 mesh (get-collision-model world type)
                                 transform (if (= (:type part) :track)
                                             (get-tail-transform part)
                                             (:transform part))
                                 scale (:scale part)
                                 [i d p] (get-mesh-collision mesh transform scale line)]
                             (if (nil? d)
                               nil
                               {:part-name part-name
                                :distance d
                                :point p
                                :index i
                                :segment-index (:segment-index part)}))))
                       parts)
        distances (filter (fn [distance]
                            (not (or (nil? distance)
                                     (= (:part-name distance) :ground-part))))
                          distances)]
    (first (sort-by :distance distances))))

(defn get-part-at [world spec]
  (:part-name (get-part-collision world spec)))

(defn get-track-head-collision [world spec]
  (if-let [track-head-name (:track-head world)]
    (let [transform (get-in world [:parts track-head-name :transform])
          mesh (:track-head-model world)
          scale (:scale mesh)
          line (get-spec-line world spec)
          collision (get-mesh-collision mesh transform scale line)]
      {:part-name track-head-name
       :track-head true
       :collision collision
       :distance (second collision)})
    nil))

(defn get-ground-collision [world spec]
  (let [plane [[0 0 0] [1 0 0] [0 0 1]]
        line (get-spec-line world spec)]
    {:part-name :ground-part
     :point (line-plane-intersection line plane)}))

(defn get-collision [world spec]
  (let [c-track-head (get-track-head-collision world spec)
        c-part (get-part-collision world spec)
        c-ground (get-ground-collision world spec)]
    (cond
      (and (nil? c-track-head)
           (nil? c-part))
      c-ground

      (distance-comparator (:distance c-track-head)
                           (:distance c-part)) c-track-head

      :else c-part)))
