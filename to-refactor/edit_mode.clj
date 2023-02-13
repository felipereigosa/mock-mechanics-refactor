(ns mockmechanics.edit-mode
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.transform :as transform]
            [mockmechanics.engine.picture :as picture]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.analytic-geometry :as ag]))

;;##############################
(declare draw-mesh!)
(declare fill-rect!)
(declare draw-rect!)
(declare draw-image!)
(declare draw-image)
(declare reset-texture)

(declare show-hint)

(declare user-message!)

(declare get-part-position)
(declare get-part-offset)
(declare get-parent-part)
(declare set-part-position)
(declare create-relative-transform)
(declare set-value-0-transform)
(declare move-part)
(declare resize-display-texture)
(declare create-display-texture)
(declare select-part)
(declare tree-will-change)
(declare tree-changed)

(declare get-spec-line)
(declare get-part-collision)
(declare get-part-at)
(declare get-collision-normal)
(declare get-collision)

(declare recompute-cable-length)

(declare add-wagon-to-track)
(declare can-place-part-at?)
(declare place-part-at)

(declare compute-children-transforms)
;;##############################

(load "edit/move")
(load "edit/sink")
(load "edit/rotate")
(load "edit/delete")
(load "edit/scale")
(load "edit/translate")
(load "edit/copy")

(defn edit-mode-entered [world]
  (dissoc-in world [:selected-part]))

(defn edit-mode-draw-3d [world]
  (doseq [[part-name part] (:parts world)]
    (if (and (= (:type part) :block)
             (not-nil? (:model part)))
      (let [transform (transform/combine
                       (:transform part)
                       (transform/create [0 0.001 0] [1 0 0 0]))
            cage (-> (:cage world)
                     (assoc-in [:scale] (:scale part))
                     (assoc-in [:transform] transform))]
        (draw-mesh! world cage)))))

(defn edit-mode-draw [world]
  (let [edit-menu (:edit-menu world)]
    (let [{:keys [image x y w h]} edit-menu]
      (fill-rect! (make-color 70 70 70) x y (+ w 30) (+ h 20))
      (draw-image! image x y))

    (let [box (get-in edit-menu [:regions (:edit-subcommand world)])
          {:keys [x y w h]} (picture/get-absolute-region box edit-menu)]
      (dotimes [i 3]
        (draw-rect! :black x y (- w i) (- h i))))))

(defn edit-mode-pressed [world event]
  (let [{:keys [x y]} event]
    (if-let [region (picture/get-region-at (:edit-menu world) x y)]
      (-> world
          (assoc-in [:edit-subcommand] region)
          (show-hint :edit region)
          (assoc-in [:region-pressed] true))
      (let [world (case (:edit-subcommand world)
                    :move (move-mode-pressed world event)
                    :sink (sink-mode-pressed world event)
                    :rotate (rotate-mode-pressed world event)
                    :scale (scale-mode-pressed world event)
                    :copy (copy-mode-pressed world event)
                    :translate (translate-mode-pressed world event)
                    world)]
        (tree-will-change world)))))

(defn edit-children [world]
  (if-let [part-name (:edited-part world)]
    (let [transform (get-in world [:parts part-name :transform])]
      (compute-children-transforms world part-name transform :parts))
    world))

(defn edit-mode-moved [world event]
  (if (:region-pressed world)
    world
    (let [world (case (:edit-subcommand world)
                  :move (move-mode-moved world event)
                  :rotate (rotate-mode-moved world event)
                  :translate (translate-mode-moved world event)
                  :sink (sink-mode-moved world event)
                  :copy (copy-mode-moved world event)
                  :scale (scale-mode-moved world event)
                  world)]
      (edit-children world))))

(defn edit-mode-released [world event]
  (if (:region-pressed world)
    (dissoc-in world [:region-pressed])
    (let [world (case (:edit-subcommand world)
                  :move (move-mode-released world event)
                  :rotate (rotate-mode-released world event)
                  :translate (translate-mode-released world event)
                  :sink (sink-mode-released world event)
                  :copy (copy-mode-released world event)
                  :scale (scale-mode-released world event)
                  :delete (delete-mode-released world event)
                  world)]
      (if (and (:control-pressed world)
               (in? (:edit-subcommand world) [:copy :translate]))
        (assoc-in world [:use-weld-groups] true)
        (tree-changed world)))))
