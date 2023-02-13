  :track
  (let [max-angle (get-in world [:parts part-name :max-angle])]
    (assoc-in world [:parts part-name :value]
              (if (nil? max-angle)
                new-value
                (within new-value 0 max-angle))))

  :speaker
  (-> world
      ((fn [w]
         (let [note (get-note (:frequency part))]
           (cond
             (and (> new-value 0.5)
                  (< (get-in world [:parts part-name :value]) 0.5))
             (note-on note)

             (and (< new-value 0.5)
                  (> (get-in world [:parts part-name :value]) 0.5))
             (note-off note)))
         w))
      (assoc-in [:parts part-name :value] new-value))

(< @avatar-active-time 10000)
(when (and (in? (:mode @world) [:simulation :graph
                                :motherboard :property
                                :avatar])
           (or
             (any-chip-active? @world)
             (> @motherboard-activation-count 0)))
  (reset! time-since-update 0))
(or (not (empty? (:spheres world)))
    ;; (:forces-active? @world)
    (spheres-moving? world)
    (not (nil? (:mouse-force world)))
    (:camera-rotating world))


(defn view-all-parts [world]
  (let [real-parts (dissoc-in (:parts world) [:ground-part])]
    (if (empty? real-parts)
      (reset world)
      (let [center (vector/multiply
                     (reduce vector/add
                             (map #(transform/get-position (:transform %))
                                  (vals real-parts)))
                     (/ 1.0 (count real-parts)))
            distance 50]
        (-> world
            (assoc-in [:camera :pivot] center)
            (assoc-in [:camera :distance] distance)
            compute)))))

(defn draw-activate-highlight! [world]
  (let [graph-box (:graph-box world)
        cx (:x graph-box)
        cy (:y graph-box)
        size 20
        points [[(- cx size) (- cy size)]
                [(- cx size) (+ cy size)]
                [(+ cx (* size 0.8)) cy]]]
    (fill-polygon! :white points)))


(let [type (get-in w [:parts part-name :type])]
  (case type
    :chip

    (if (float= value 1.0)
      (activate-chip w part-name)
      w))

  :speaker
  (let [value (get-element-value w motherboard part-name)
        part (get-in w [:parts part-name])
        note (get-note (:frequency part))]
    (if (float= value 1.0)
      (note-on note)
      (note-off note))
    w)

  :wagon
  (let [wagon (get-in w [:parts part-name])
        value (get-element-value w motherboard part-name)
        scaled-value (/ value (reduce + (:track-lengths wagon)))]
    (assoc-in w [:parts part-name :value] scaled-value))

  :track
  (-> w
      (assoc-in [:parts part-name :value]
                (get-element-value w motherboard part-name))
      (update-in [:driven-parts] #(vec (distinct (conj % part-name)))))

  (assoc-in w [:parts part-name :value]
            (get-element-value w motherboard part-name)))x
