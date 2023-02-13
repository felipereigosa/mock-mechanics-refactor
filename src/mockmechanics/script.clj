;; (defn map-bindings [names values]
;;   (flatten (vec (apply merge (map (fn [a b]
;;                                     (if (= a '_)
;;                                       nil
;;                                       {a b}))
;;                                   names values)))))

;; (declare get-pixel-coordinates)

;; (defn get-color-at [world spec]
;;   (let [collision (get-part-collision world spec)
;;         part-name (:part-name collision)
;;         part (get-in world [:parts part-name])
;;         color (if (= (:type part) :display)
;;                 (let [image (get-in part [:texture :image])
;;                       [px py] (get-pixel-coordinates world spec)
;;                       c (get-color-vector (get-pixel image (+ 10 (* 20 px)) (+ 10 (* 20 py))))
;;                       [r g b _] (map #(int (* 255 %)) c)]
;;                   (new Color r g b))
;;                 (:color part))]
;;     (get-reverse-color color)))

;; (defn process-code [code pins]
;;   (let [names (nth code 1)
;;         body (nthrest code 2)
;;         bindings (map-bindings names pins)
;;         helpers '[get-value (fn [name]
;;                               (let [part (get-in @world [:parts name])
;;                                     value (:value part)]
;;                                 (if (= (:type part) :wagon)
;;                                   (* value (reduce + (:track-lengths part)))
;;                                   value)))

;;                   set-value (fn [name value]
;;                               (let [part (get-in @world [:parts name])
;;                                     value (if (= (:type part) :wagon)
;;                                             (/ value (reduce + (:track-lengths part)))
;;                                             value)]
;;                                 (set-thing! [:parts name :value] value)
;;                                 (sleep 50)))

;;                   chip-active? #(chip-active? @world %)

;;                   on? (fn [part-name]
;;                         (float= (get-value part-name) 1.0))

;;                   off? (fn [part-name]
;;                          (float= (get-value part-name) 0.0))

;;                   get-children #(keys (get-thing! [:parts % :children]))

;;                   get-part-position #(get-part-position
;;                                        (compute-transforms @world :parts) %)

;;                   get-color #(get-thing! [:parts % :color])

;;                   set-color (fn [part-name color]
;;                               (set-thing! [:parts part-name :color] color)
;;                               (update-thing! [] tree-changed))

;;                   wait (fn [pred]
;;                          (while (pred) (sleep 50))
;;                          (sleep 100))

;;                   activate-chip (fn [chip-name]
;;                                   (update-thing! [] #(activate-chip % chip-name))
;;                                   (wait #(chip-active? chip-name)))

;;                   activate-button (fn [button-name]
;;                                     (set-value button-name 1)
;;                                     (wait #(on? button-name)))

;;                   activate-wagon (fn [wagon-name]
;;                                    (set-value wagon-name 1)
;;                                    (wait #(not (off? wagon-name)))
;;                                    (set-value wagon-name 0.5))

;;                   activate (fn [part-name]
;;                              (let [world @world
;;                                    part (get-in world [:parts part-name])]
;;                                (case (:type part)
;;                                  :wagon (activate-wagon part-name)
;;                                  :button (activate-button part-name)
;;                                  :chip (activate-chip part-name))))

;;                   = (fn [a b]
;;                       (if (and (number? a)
;;                                (number? b))
;;                         (float= a b)
;;                         (= a b)))

;;                   clear-display (fn [display-name color]
;;                                   (let [display (get-thing! [:parts display-name])
;;                                         mesh (:texture display)]
;;                                     (clear (:image mesh) color)
;;                                     (gl-thread (reset-texture mesh))))

;;                   set-pixel (fn [display-name x y color]
;;                               (let [display (get-thing! [:parts display-name])
;;                                     mesh (:texture display)]
;;                                 (set-pixel! display x y color)
;;                                 (gl-thread (reset-texture mesh))))

;;                   draw-pattern (fn [display-name pattern x y color]
;;                                  (let [display (get-thing! [:parts display-name])]
;;                                    (dotimes [yo (count pattern)]
;;                                      (dotimes [xo (count (first pattern))]
;;                                        (if (= (get-in pattern [yo xo]) 1)
;;                                          (set-pixel display-name (+ x xo) (+ y yo) color))))))
;;                   ]]
;;     `(do
;;        (require '[mockmechanics.core :refer :all])
;;        (require '[mockmechanics.library.util :refer :all])

;;        (let [~@bindings
;;              ~@helpers]
;;          ~@body))))

;; (defn get-sorted-pin-list [world motherboard-name]
;;   (let [motherboard (get-in world [:parts motherboard-name])
;;         helper (fn [[name pin]]
;;                  (let [type (get-in world [:parts name :type])]
;;                    [name (:x pin)]))]
;;     (map first (sort-by last (map helper (:pins motherboard))))))

;; (def motherboard-activation-count (atom 0))

;; (defn get-script-filename [motherboard-name]
;;   (str "temp/" (dekeyword motherboard-name) "-script.clj"))

;; (defn run-script [world motherboard-name pin-name]
;;   (try
;;     (let [motherboard (get-in world [:parts motherboard-name])
;;           sorted-pins (get-sorted-pin-list world motherboard-name)
;;           text (read-string (str "(do" (:script motherboard) ")"))
;;           code (process-code text sorted-pins)]
;;       (.start
;;         (new Thread
;;              (proxy [Runnable] []
;;                (run []
;;                  (swap! motherboard-activation-count inc)
;;                  ((eval code) pin-name)
;;                  (swap! motherboard-activation-count dec))))))
;;     (catch Exception e
;;       (user-message! e)
;;       (user-message! "script failed")))
;;   world)

(defn run-script [world motherboard-name]
  ;;##########################################
  world)

;;################################
;; change :active-graphs to active?
;; set when script starts and unset when it ends
;; make sure it unsets even if script is interrupted/run again
