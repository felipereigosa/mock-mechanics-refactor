(ns mockmechanics.engine.graphics
  (:require [mockmechanics.engine.util :refer :all]
            [mockmechanics.engine.matrix :as matrix]
            [mockmechanics.engine.vector :as vector]
            [mockmechanics.engine.transform :as transform]
            [mockmechanics.engine.world :refer [world]])
  (:import (java.awt image.BufferedImage
                     geom.Ellipse2D$Double
                     RenderingHints
                     Font
                     Polygon
                     geom.AffineTransform
                     AlphaComposite)
           (org.lwjgl.opengl GL11 GL12 GL13 GL20 GL30)
           (java.nio ByteBuffer ByteOrder)
           javax.imageio.ImageIO
           java.io.File))

(defn check-shader [shader]
  (let [status (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS)]
    (when (= status 0)
      (println (GL20/glGetShaderInfoLog shader))
      (GL20/glDeleteShader shader))))

(defn check-program [program]
  (let [status (GL20/glGetProgrami program GL20/GL_LINK_STATUS)]
    (when (= status 0)
      (println (GL20/glGetProgramInfoLog program))
      (GL20/glDeleteProgram program))))

(defn load-shader [filename type]
  (let [shader (GL20/glCreateShader (if (= type :fragment)
                                      GL20/GL_FRAGMENT_SHADER
                                      GL20/GL_VERTEX_SHADER))
        source (slurp filename)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (check-shader shader)
    shader))

(defn compile-program [vertex-filename fragment-filename]
  (let [vertex-shader (load-shader vertex-filename :vertex)
        fragment-shader (load-shader fragment-filename :fragment)
        program (GL20/glCreateProgram)]

    (GL20/glAttachShader program vertex-shader)
    (GL20/glAttachShader program fragment-shader)
    (GL20/glLinkProgram program)
    (check-program program)
    program))

(defn make-int-buffer [size]
  (let [bb (ByteBuffer/allocateDirect size)]
    (.order bb (ByteOrder/nativeOrder))
    (.asIntBuffer bb)))

(defn get-attribute-names [index]
  (let [num-active (GL20/glGetProgrami index GL20/GL_ACTIVE_ATTRIBUTES)]
    (map (fn [i]
           (let [size (make-int-buffer 100)
                 type (make-int-buffer 100)
                 name (GL20/glGetActiveAttrib index i size type)
                 index (.indexOf name "[")]
             (if (pos? index)
               (subs name 0 index)
               name)))
         (range num-active))))

(defn get-uniform-names [index]
  (let [num-active (GL20/glGetProgrami index GL20/GL_ACTIVE_UNIFORMS)]
    (map (fn [i]
           (let [size (make-int-buffer 100)
                 type (make-int-buffer 100)
                 name (GL20/glGetActiveUniform index i size type)
                 index (.indexOf name "[")]
             (if (pos? index)
               (subs name 0 index)
               name)))
         (range num-active))))

(defn location-name->keyword [name]
  (keyword (apply str (clojure.string/replace name #"_" "-"))))

(defn create-program [base-name]
  (let [v-name (str "res/" base-name "-vert.glsl")
        f-name (str "res/" base-name "-frag.glsl")
        index (compile-program v-name f-name)
        attribute-names (get-attribute-names index)
        uniform-names (get-uniform-names index)
        attributes (apply merge
                          (map (fn [name]
                                 {(location-name->keyword name)
                                  (GL20/glGetAttribLocation index name)})
                               attribute-names))
        uniforms (apply merge
                        (map (fn [name]
                               {(location-name->keyword name)
                                (GL20/glGetUniformLocation index name)})
                             uniform-names))]
    {:index index
     :uniforms uniforms
     :attributes attributes}))

(defn new-image [width height]
  (new BufferedImage
       width
       height
       (. BufferedImage TYPE_INT_ARGB)))

(defn open-image [filename]
  (ImageIO/read (new File filename)))

(defn get-image-graphics [image]
  (let [g (.getGraphics image)]
    (.setRenderingHint g
                       RenderingHints/KEY_ANTIALIASING
                       RenderingHints/VALUE_ANTIALIAS_ON)
    g))

(defn clear
  ([image]
   (let [g (get-image-graphics image)
         w (.getWidth image)
         h (.getHeight image)]
     (.setComposite g (AlphaComposite/getInstance
                        AlphaComposite/CLEAR))
     (.fillRect g 0 0 w h)
     (.setComposite g (AlphaComposite/getInstance
                        AlphaComposite/SRC_OVER))))
  ([image color]
   (let [g (get-image-graphics image)
         w (.getWidth image)
         h (.getHeight image)]
     (.setColor g (get-color color))
     (.fillRect g 0 0 w h))))

(defn draw-pixel [image color x y]
  (when (and
          (<= 0 x (dec (.getWidth image)))
          (<= 0 y (dec (.getHeight image))))
    (.setRGB image (int x) (int y) (.getRGB (get-color color))))
  image)

(defn fill-rect [image color x y w h]
  (let [g (get-image-graphics image)
        hw (/ w 2)
        hh (/ h 2)]
    (.setColor g (get-color color))
    (.fillRect g (- x hw) (- y hh) w h)))

(defn draw-rect [image color x y w h]
  (let [g (get-image-graphics image)
        hw (/ w 2)
        hh (/ h 2)]
    (.setColor g (get-color color))
    (.drawRect g (- x hw) (- y hh) w h)))

(defn fill-circle [image color x y r]
  (let [g (get-image-graphics image)]
    (.setColor g (get-color color))
    (.fill g (new Ellipse2D$Double (- x r) (- y r) (* 2 r) (* 2 r)))))

(defn draw-circle [image color x y r]
  (let [g (get-image-graphics image)]
    (.setColor g (get-color color))
    (.draw g (new Ellipse2D$Double (- x r) (- y r) (* 2 r) (* 2 r)))))

(defn get-text-width! [text size]
  (let [image (get-in @world [:ortho-mesh :image])
        g (get-image-graphics image)
        font (new Font "Dialog" Font/PLAIN size)]
    (.stringWidth (.getFontMetrics g font) text)))

(defn draw-text [image color text x y size]
  (let [g (get-image-graphics image)]
    (.setFont g (new Font "Dialog" Font/PLAIN size))
    (.setColor g (get-color color))
    (.drawString g text (int x) (int y))))

(defn draw-text-mono [image color text x y size]
  (let [g (get-image-graphics image)]
    (.setFont g (new Font "monospaced" Font/PLAIN size))
    (.setColor g (get-color color))
    (.drawString g text (int x) (int y))))

(defn draw-ellipse [image color rect]
  (let [g (get-image-graphics image)]
    (.setColor g (get-color color))
    (let [w (* (:w rect) 1.0)
          h (* (:h rect) 1.0)
          ellipse (new Ellipse2D$Double
                       (- (/ w 2))
                       (- (/ h 2))
                       w h)
          angle (to-radians (:angle rect))
          ellipse (.createTransformedShape
                    (AffineTransform/getRotateInstance angle)
                    ellipse)
          ellipse (.createTransformedShape
                    (AffineTransform/getTranslateInstance
                      (:x rect) (:y rect))
                    ellipse)]
      (.draw g ellipse)
      (fill-circle image color (:x rect) (:y rect) 2))))

(defn draw-line [image color x1 y1 x2 y2]
  (let [g (get-image-graphics image)]
    (.setColor g (get-color color))
    (.drawLine g x1 y1 x2 y2)))

(defn fill-polygon [image color points]
  (let [g (get-image-graphics image)]
    (.setColor g (get-color color))
    (let [polygon (new Polygon)]
      (doseq [[x y] points]
        (.addPoint polygon x y))
      (.fillPolygon g polygon))))

(defn draw-polygon [image color points]
  (let [g (get-image-graphics image)]
    (.setColor g (get-color color))
    (let [polygon (new Polygon)]
      (doseq [[x y] points]
        (.addPoint polygon x y))
      (.drawPolygon g polygon))))

(defn draw-image [image image2 x y & corner]
  (let [g (get-image-graphics image)
        w (.getWidth image2)
        h (.getHeight image2)
        x (if (first corner) x (- x (/ w 2)))
        y (if (first corner) y (- y (/ h 2)))]
    (.drawImage g image2 (int x) (int y) nil)))

(defn image->buffer [image]
  (let [w (.getWidth image)
        h (.getHeight image)
        pixels (int-array (* w h))
        bb (ByteBuffer/allocateDirect (* w h 4))]
    (.getRGB image 0 0 w h pixels 0 w)
    (let [ib (.asIntBuffer bb)]
      (.put ib pixels)
      bb)))

(defn reset-texture [mesh]
  (let [id (:texture-id mesh)
        image (:image mesh)
        width (.getWidth image)
        height (.getHeight image)
        buffer (image->buffer image)]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D id)
    (GL11/glTexSubImage2D GL11/GL_TEXTURE_2D 0 0 0 width height
                          GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    mesh))

(defn set-texture [mesh]
  (let [id (:texture-id mesh)
        image (:image mesh)
        width (.getWidth image)
        height (.getHeight image)
        buffer (image->buffer image)]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)

    (GL11/glBindTexture GL11/GL_TEXTURE_2D id)
    (GL11/glPixelStorei GL11/GL_UNPACK_ALIGNMENT 1)
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA width height 0
                       GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
    (GL30/glGenerateMipmap GL11/GL_TEXTURE_2D)

    (GL11/glTexParameteri GL11/GL_TEXTURE_2D
                          GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D
                          GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER
                          GL11/GL_LINEAR)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER
                          GL11/GL_LINEAR)
    mesh))

(defn clear-depth-buffer []
  (GL11/glClear GL11/GL_DEPTH_BUFFER_BIT))

(defn clear!
  ([]
   (let [mesh (get-in @world [:ortho-mesh])]
     (clear (:image mesh))
     (reset-texture mesh)))
  ([color]
   (let [mesh (get-in @world [:ortho-mesh])]
     (clear (:image mesh) color)
     (reset-texture mesh))))

(defn draw-pixel! [color x y]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-pixel (:image mesh) color x y)
    (reset-texture mesh)))

(defn fill-rect! [color x y w h]
  (let [mesh (get-in @world [:ortho-mesh])]
    (fill-rect (:image mesh) color x y w h)
    (reset-texture mesh)))

(defn draw-rect! [color x y w h]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-rect (:image mesh) color x y w h)
    (reset-texture mesh)))

(defn fill-circle! [color x y r]
  (let [mesh (get-in @world [:ortho-mesh])]
    (fill-circle (:image mesh) color x y r)
    (reset-texture mesh)))

(defn draw-circle! [color x y r]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-circle (:image mesh) color x y r)
    (reset-texture mesh)))

(defn draw-text! [color text x y size]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-text (:image mesh) color text x y size)
    (reset-texture mesh)))

(defn draw-text-in-box! [text color size box]
  (let [w (* 0.5 (get-text-width! text size))]
    (draw-text! color text (- (:x box) w) (+ (:y box) 5) size)))

(defn draw-ellipse! [color rect]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-ellipse (:image mesh) color rect)
    (reset-texture mesh)))

(defn draw-line! [color x1 y1 x2 y2]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-line (:image mesh) color x1 y1 x2 y2)
    (reset-texture mesh)))

(defn fill-polygon! [color points]
  (let [mesh (get-in @world [:ortho-mesh])]
    (fill-polygon (:image mesh) color points)
    (reset-texture mesh)))

(defn draw-polygon! [color points]
  (let [mesh (get-in @world [:ortho-mesh])]
    (draw-polygon (:image mesh) color points)
    (reset-texture mesh)))

(defn draw-image! [image2 x y & corner]
  (let [mesh (get-in @world [:ortho-mesh])]
    (apply draw-image (:image mesh) image2 x y corner)
    (reset-texture mesh)))

(defn set-mesh-color [mesh color]
  (let [color (get-color color)
        r (/ (.getRed color) 255)
        g (/ (.getGreen color) 255)
        b (/ (.getBlue color) 255)]
    (assoc-in mesh [:color] [r g b 1.0])))

(defn compute-normals [vertices]
  (flatten (map (fn [[a b c]]
                  (let [v1 (vector/subtract b a)
                        v2 (vector/subtract c a)
                        v3 (vector/cross-product v1 v2)
                        nv3 (vector/normalize v3)]
                    (list nv3 nv3 nv3)))
                (partition 3 (partition 3 vertices)))))

(defn get-float-buffer [seq]
  (let [array (if (vector? seq)
                (float-array seq)
                seq)
        bb (ByteBuffer/allocateDirect (* (count array) 4))]
    (.order bb (ByteOrder/nativeOrder))
    (let [fb (.asFloatBuffer bb)]
      (.put fb array)
      (.position fb 0)
      fb)))

(defn draw-lighted-mesh! [world mesh]
  (let [num-vertices (/ (.capacity (:vertices-buffer mesh)) 3)
        program (get-in world [:programs (:program mesh)])
        program-index (:index program)
        attributes (:attributes program)
        uniforms (:uniforms program)
        {:keys [position rotation scale]} mesh
        model-matrix (matrix/multiply
                       (apply matrix/get-scale scale)
                       (transform/get-matrix [position rotation]))
        view-matrix (:view-matrix world)
        projection-matrix (:projection-matrix world)
        mv-matrix (matrix/multiply model-matrix view-matrix)
        mvp-matrix (matrix/multiply mv-matrix projection-matrix)
        itmv-matrix (matrix/get-transpose (matrix/get-inverse mv-matrix))]

    (GL20/glUseProgram program-index)
    (GL20/glUniformMatrix4fv (:itmv-matrix uniforms) false
                             (get-float-buffer itmv-matrix))

    (GL20/glUniformMatrix4fv (:mvp-matrix uniforms) false
                             (get-float-buffer mvp-matrix))

    (GL20/glVertexAttribPointer (:position attributes) 3 GL11/GL_FLOAT
                                false 0 (:vertices-buffer mesh))

    (GL20/glEnableVertexAttribArray (:position attributes))

    (GL20/glVertexAttribPointer (:normal attributes) 3 GL11/GL_FLOAT
                                false 0 (:normals-buffer mesh))
    (GL20/glEnableVertexAttribArray (:normal attributes))

    (if-let [[r g b a] (:color mesh)]
      (GL20/glUniform4f (:material-color uniforms) r g b a)
      (do
        (GL20/glVertexAttribPointer (:texture-coordinates attributes)
                                    2 GL11/GL_FLOAT false 0
                                    (:texture-coordinates-buffer mesh))
        (GL20/glEnableVertexAttribArray (:texture-coordinates attributes))
        (GL13/glActiveTexture GL13/GL_TEXTURE0)
        (GL11/glBindTexture GL11/GL_TEXTURE_2D (:texture-id mesh))
        (GL20/glUniform1i (:texture-diffuse uniforms) 0)))

    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 num-vertices)))

(defn draw-colored-mesh! [world mesh]
  (let [num-vertices (/ (.capacity (:vertices-buffer mesh)) 3)
        program (get-in world [:programs (:program mesh)])
        program-index (:index program)
        attributes (:attributes program)
        uniforms (:uniforms program)
        {:keys [position rotation scale]} mesh
        model-matrix (matrix/multiply
                       (apply matrix/get-scale scale)
                       (transform/get-matrix [position rotation]))
        view-matrix (:view-matrix world)
        projection-matrix (:projection-matrix world)
        mv-matrix (matrix/multiply model-matrix view-matrix)
        mvp-matrix (matrix/multiply mv-matrix projection-matrix)
        itmv-matrix (matrix/get-transpose (matrix/get-inverse mv-matrix))]

    (GL20/glUseProgram program-index)
    (GL20/glUniformMatrix4fv (:itmv-matrix uniforms) false
                             (get-float-buffer itmv-matrix))
    (GL20/glUniformMatrix4fv (:mvp-matrix uniforms) false
                             (get-float-buffer mvp-matrix))

    (GL20/glVertexAttribPointer (:position attributes) 3 GL11/GL_FLOAT
                                false 0 (:vertices-buffer mesh))
    (GL20/glEnableVertexAttribArray (:position attributes))

    (GL20/glVertexAttribPointer (:normal attributes) 3 GL11/GL_FLOAT
                                false 0 (:normals-buffer mesh))
    (GL20/glEnableVertexAttribArray (:normal attributes))

    (GL20/glVertexAttribPointer (:color attributes) 4 GL11/GL_FLOAT
                                false 0 (:colors-buffer mesh))
    (GL20/glEnableVertexAttribArray (:color attributes))

    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 num-vertices)))

(defn draw-textured-mesh! [world mesh]
  (let [num-vertices (/ (.capacity (:vertices-buffer mesh)) 3)
        program (get-in world [:programs (:program mesh)])
        program-index (:index program)
        attributes (:attributes program)
        uniforms (:uniforms program)
        {:keys [position rotation scale]} mesh
        model-matrix (matrix/multiply
                       (apply matrix/get-scale scale)
                       (transform/get-matrix [position rotation]))
        view-matrix (:view-matrix world)
        projection-matrix (:projection-matrix world)
        mv-matrix (matrix/multiply model-matrix view-matrix)
        mvp-matrix (matrix/multiply mv-matrix projection-matrix)
        itmv-matrix (matrix/get-transpose (matrix/get-inverse mv-matrix))]

    (GL20/glUseProgram program-index)
    (GL20/glUniformMatrix4fv (:itmv-matrix uniforms) false
                             (get-float-buffer itmv-matrix))

    (GL20/glUniformMatrix4fv (:mvp-matrix uniforms) false
                             (get-float-buffer mvp-matrix))

    (GL20/glVertexAttribPointer (:position attributes) 3 GL11/GL_FLOAT
                                false 0 (:vertices-buffer mesh))

    (GL20/glEnableVertexAttribArray (:position attributes))

    (GL20/glVertexAttribPointer (:normal attributes) 3 GL11/GL_FLOAT
                                false 0 (:normals-buffer mesh))
    (GL20/glEnableVertexAttribArray (:normal attributes))

    (GL20/glVertexAttribPointer (:texture-coordinates attributes)
                                2 GL11/GL_FLOAT false 0
                                (:texture-coordinates-buffer mesh))
    (GL20/glEnableVertexAttribArray (:texture-coordinates attributes))

    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:texture-id mesh))
    (GL20/glUniform1i (:texture-diffuse uniforms) 0)

    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 num-vertices)))

(defn create-mesh [vertices position rotation
                   scale skin tex-coords normals]
  (let [scale (if (vector? scale)
                scale
                (vec (repeat 3 scale)))
        vertices (vec (flatten vertices))
        normals (if (empty? normals)
                  (vec (compute-normals vertices))
                  (vec (flatten normals)))
        base-mesh {:vertices vertices
                   :vertices-buffer (get-float-buffer vertices)
                   :normals normals
                   :normals-buffer (get-float-buffer normals)
                   :position position
                   :rotation rotation
                   :scale scale}]
    (cond
      (string? skin)
      (let [texture-id (GL11/glGenTextures)
            tex-coords (vec (flatten tex-coords))]
        (-> base-mesh
            (assoc-in [:draw-fn] draw-textured-mesh!)
            (assoc-in [:program] :textured)
            (assoc-in [:image] (open-image skin))
            (assoc-in [:texture-coordinates] tex-coords)
            (assoc-in [:texture-coordinates-buffer]
                      (get-float-buffer tex-coords))
            (assoc-in [:texture-id] texture-id)
            (set-texture)))

      (sequential? skin)
      (let [colors (vec (flatten skin))]
        (-> base-mesh
            (assoc-in [:colors] colors)
            (assoc-in [:colors-buffer] (get-float-buffer colors))
            (assoc-in [:draw-fn] draw-colored-mesh!)
            (assoc-in [:program] :colored)))

      :else
      (let [color (get-color skin)
            r (/ (.getRed color) 255.0)
            g (/ (.getGreen color) 255.0)
            b (/ (.getBlue color) 255.0)]
        (-> base-mesh
            (assoc-in [:color] [r g b 1.0])
            (assoc-in [:draw-fn] draw-lighted-mesh!)
            (assoc-in [:program] :flat))))))

(defn find-line [lines start]
  (find-if #(.startsWith % start) lines))

(defn parse-line [line]
  (map read-string (rest (.split line " "))))

(defn parse-material [directory lines]
  (let [name (subs (find-line lines "newmtl") 7)
        texture-line (find-line lines "map_Kd")]
    {name {:diffuse (parse-line (find-line lines "Kd"))
           :texture (if texture-line
                      (str directory "/" (subs texture-line 7)))}}))

(defn parse-materials [filename]
  (let [lines (read-lines filename)
        lines (filter (fn [line]
                        (or (.startsWith line "newmtl")
                            (.startsWith line "Kd")
                            (.startsWith line "map_Kd")))
                      lines)
        directory (subs filename 0 (.lastIndexOf filename "/"))
        materials (create-groups #(.startsWith % "newmtl") lines)]
    (apply merge (cons {"white" {:diffuse [1 1 1]
                                 :texture nil}}
                       (map #(parse-material directory %) materials)))))

(defn parse-line-with-slashes [line]
  (map (fn [item]
         (map read-string (filter (comp not empty?)
                                  (.split item "/"))))
       (rest (.split line " "))))

(defn use-indices [vector indices]
  (let [min-index (apply min indices)
        indices (map #(- % min-index) indices)]
    (map (fn [v]
           (nth vector v))
         indices)))

(defn create-colors [lines materials]
  (let [groups (create-groups #(.startsWith % "usemtl") lines)]
    (apply concat
           (map (fn [group]
                  (let [n (* 3 (count
                                 (filter #(.startsWith % "f ") group)))
                        color (conj (vec (:diffuse
                                          (get materials
                                               (subs (first group) 7))))
                                    1)]
                    (repeat n color)))
                groups))))

(defn create-model-mesh [filename position rotation scale color]
  (with-open [reader (clojure.java.io/reader filename)]
    (let [materials-filename (-> filename
                                 (subs 0 (.lastIndexOf filename "."))
                                 (str ".mtl"))
          materials (parse-materials materials-filename)
          lines (filter (fn [line]
                          (or (.startsWith line "o")
                              (.startsWith line "v")
                              (.startsWith line "vn")
                              (.startsWith line "vt")
                              (.startsWith line "f")
                              (.startsWith line "usemtl")))
                        (line-seq reader))
          v (map parse-line (filter #(.startsWith % "v ") lines))
          n (map parse-line (filter #(.startsWith % "vn") lines))
          t (map parse-line (filter #(.startsWith % "vt") lines))
          faces (mapcat parse-line-with-slashes
                        (filter #(.startsWith % "f") lines))
          vertices (use-indices v (map first faces))
          normals (use-indices n (map last faces))
          texture-name (some :texture (vals materials))
          texture-coords (if texture-name
                           (use-indices t (map #(nth % 1) faces))
                           [])
          texture-coords (map (fn [[u v]]
                                [u (- 1.0 v)])
                              texture-coords)
          skin (or color
                   texture-name
                   (create-colors lines materials))]
      (create-mesh vertices position rotation scale
                   skin texture-coords normals))))

(defn draw-lines! [world mesh]
  (let [num-vertices (/ (.capacity (:vertices-buffer mesh)) 3)
        [r g b a] (:color mesh)
        program (get-in world [:programs (:program mesh)])
        program-index (:index program)
        attributes (:attributes program)
        uniforms (:uniforms program)
        {:keys [position rotation scale]} mesh
        model-matrix (matrix/multiply
                       (apply matrix/get-scale scale)
                       (transform/get-matrix [position rotation]))
        view-matrix (:view-matrix world)
        projection-matrix (:projection-matrix world)
        mv-matrix (matrix/multiply model-matrix view-matrix)
        mvp-matrix (matrix/multiply mv-matrix projection-matrix)]
    (GL20/glUseProgram program-index)
    (GL20/glUniformMatrix4fv (:mvp-matrix uniforms) false
                             (get-float-buffer mvp-matrix))

    (GL20/glVertexAttribPointer (:position attributes) 3 GL11/GL_FLOAT
                                false 0 (:vertices-buffer mesh))
    (GL20/glEnableVertexAttribArray (:position attributes))

    (GL20/glUniform4f (:material-color uniforms) r g b a)
    (GL11/glDrawArrays GL11/GL_LINES 0 num-vertices)))

(defn create-line-mesh [a b color]
  (let [vertices (vec (concat a b))
        color (get-color color)
        r (/ (.getRed color) 255)
        g (/ (.getGreen color) 255)
        b (/ (.getBlue color) 255)
        line {:vertices-buffer (get-float-buffer vertices)
              :color [r g b 1.0]
              :position [0 0 0]
              :rotation [1 0 0 0]
              :program :basic
              :scale [1 1 1]
              :draw-fn draw-lines!}]
    line))

(defn draw-mesh! [world mesh]
  (let [draw-fn (:draw-fn mesh)]
    (draw-fn world mesh)))

(defn get-grid-vertices [num-cells cell-size]
  (let [hw (/ (* cell-size num-cells) 2)
        seq (map (fn [val]
                   (- (* val cell-size) hw))
                 (range (inc num-cells)))
        min (first seq)
        max (last seq)
        z-parallel (mapcat (fn [x]
                             [x 0 min x 0 max])
                           seq)
        x-parallel (mapcat (fn [z]
                             [min 0 z max 0 z])
                           seq)]
    (vec (concat z-parallel x-parallel))))

(defn create-grid-mesh [num-cells size]
  (let [vertices (get-grid-vertices num-cells size)
        color (get-color :black)
        r (/ (.getRed color) 255)
        g (/ (.getGreen color) 255)
        b (/ (.getBlue color) 255)]
    {:vertices-buffer (get-float-buffer vertices)
     :color [r g b 1.0]
     :position [0 0 0]
     :rotation [0 1 0 0]
     :program :basic
     :scale [1 1 1]
     :draw-fn draw-lines!}))

(defn draw-ortho-mesh! [world mesh]
  (let [num-vertices (/ (.capacity (:vertices-buffer mesh)) 3)
        program (get-in world [:programs :ortho])
        program-index (:index program)
        attributes (:attributes program)
        uniforms (:uniforms program)]

    (GL20/glUseProgram program-index)

    (GL20/glVertexAttribPointer (:position attributes) 3 GL11/GL_FLOAT
                                false 0 (:vertices-buffer mesh))
    (GL20/glEnableVertexAttribArray (:position attributes))

    (GL20/glVertexAttribPointer (:texture-coordinates attributes)
                                2 GL11/GL_FLOAT false 0
                                (:texture-coordinates-buffer mesh))
    (GL20/glEnableVertexAttribArray (:texture-coordinates attributes))

    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:texture-id mesh))
    (GL20/glUniform1i (:texture-diffuse uniforms) 0)

    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 num-vertices)))

(defn create-ortho-mesh [width height]
  (let [image (new-image width height)
        vertices [-1 -1  0   1 -1  0   -1  1  0
                  1 -1  0   1  1  0   -1  1  0]
        texture-coordinates [0 1   1 1   0 0
                             1 1   1 0   0 0]
        texture-id (GL11/glGenTextures)
        mesh {:vertices-buffer (get-float-buffer vertices)
              :image image
              :texture-coordinates-buffer
              (get-float-buffer texture-coordinates)
              :texture-id texture-id}]
    (set-texture mesh)))

(defn create-wireframe-mesh [vertices position rotation scale color-name]
  (let [color (get-color color-name)
        r (/ (.getRed color) 255)
        g (/ (.getGreen color) 255)
        b (/ (.getBlue color) 255)]
    {:vertices-buffer (get-float-buffer vertices)
     :color [r g b 1.0]
     :position position
     :rotation rotation
     :program :basic
     :scale scale
     :draw-fn draw-lines!}))

(defn create-wireframe-cube [position rotation scale color-name]
  (let [corners [[-0.5 0.5 0.5]
                 [-0.5 -0.5 0.5]
                 [-0.5 0.5 -0.5]
                 [-0.5 -0.5 -0.5]
                 [0.5 0.5 0.5]
                 [0.5 -0.5 0.5]
                 [0.5 0.5 -0.5]
                 [0.5 -0.5 -0.5]]
        indices [0 1 1 3 3 2 2 0
                 4 5 5 7 7 6 6 4
                 0 4 2 6 3 7 1 5]
        vertices (vec (flatten (map (fn [index]
                                      (nth corners index)) indices)))]
    (create-wireframe-mesh vertices position rotation
                           scale color-name)))

(defn draw-animated-mesh! [world mesh]
  (let [num-vertices (/ (.capacity (:vertices-buffer mesh)) 3)
        program (get-in world [:programs (:program mesh)])
        program-index (:index program)
        attributes (:attributes program)
        uniforms (:uniforms program)
        {:keys [position rotation scale]} mesh
        model-matrix (matrix/multiply
                       (apply matrix/get-scale scale)
                       (transform/get-matrix [position rotation]))
        view-matrix (:view-matrix world)
        projection-matrix (:projection-matrix world)
        mv-matrix (matrix/multiply model-matrix view-matrix)
        mvp-matrix (matrix/multiply mv-matrix projection-matrix)
        itmv-matrix (matrix/get-transpose (matrix/get-inverse mv-matrix))]

    (GL20/glUseProgram program-index)
    (GL20/glUniformMatrix4fv (:itmv-matrix uniforms) false
                             (get-float-buffer itmv-matrix))
    (GL20/glUniformMatrix4fv (:mvp-matrix uniforms) false
                             (get-float-buffer mvp-matrix))

    (GL20/glVertexAttribPointer (:position attributes) 3 GL11/GL_FLOAT
                                false 0 (:vertices-buffer mesh))
    (GL20/glEnableVertexAttribArray (:position attributes))

    (GL20/glVertexAttribPointer (:normal attributes) 3 GL11/GL_FLOAT
                                false 0 (:normals-buffer mesh))
    (GL20/glEnableVertexAttribArray (:normal attributes))

    (GL20/glVertexAttribPointer (:color attributes) 4 GL11/GL_FLOAT
                                false 0 (:colors-buffer mesh))
    (GL20/glEnableVertexAttribArray (:color attributes))

    (GL20/glVertexAttribPointer (:weights attributes) 4 GL11/GL_FLOAT
                                false 0 (:weights-buffer mesh))
    (GL20/glEnableVertexAttribArray (:weights attributes))

    (GL20/glVertexAttribPointer (:bone-indices attributes) 4 GL11/GL_FLOAT
                                false 0 (:bone-indices-buffer mesh))
    (GL20/glEnableVertexAttribArray (:bone-indices attributes))

    (GL20/glUniformMatrix4fv (:bone-matrices uniforms)
                             false
                             (get-float-buffer
                               (nth (:bone-matrices mesh)
                                    (round (:index mesh)))))

    (GL20/glUniformMatrix4fv (:inverse-bind-pose-matrices uniforms)
                             false
                             (get-float-buffer
                               (:inverse-bind-pose-matrices mesh)))

    (GL11/glDrawArrays GL11/GL_TRIANGLES 0 num-vertices)))

(defn blender->opengl [matrix]
  (let [[m00 m10 m20 m30
         m01 m11 m21 m31
         m02 m12 m22 m32
         m03 m13 m23 m33] matrix]
    [m00 m02 (- m01) m03
     m10 m12 (- m11) m13
     m20 m22 (- m21) m23
     m30 m32 (- m31) m33]))

(defn change-extension [filename extension]
  (-> filename
      (subs 0 (.lastIndexOf filename "."))
      (str "." extension)))

(defn read-floats [lines n]
  (vec (partition
         n (read-string (str "[" (clojure.string/join " " lines) "]")))))

(defn match-order [coll order]
  (let [indices (zipmap order (range))
        f #(get indices (subs (first %) 2))]
    (sort #(< (f %1) (f %2)) coll)))

(defn parse-animation [filename order]
  (with-open [reader (clojure.java.io/reader filename)]
    (let [lines (doall (line-seq reader))
          lines (remove (fn [line]
                          (or (empty? line)
                              (.startsWith line ";;")))
                        lines)
          num-bones (read-string (subs (first lines) 12))
          num-vertices (read-string (subs (second lines) 15))
          lines (nthrest lines 3)
          n (+ (* 2 num-vertices) (count order))
          weight-lines (take n lines)
          weight-groups (create-groups #(.startsWith % ">") weight-lines)
          weight-groups (match-order weight-groups order)
          [weights bone-indices] (reduce (fn [result item]
                                           (let [item (rest item)
                                                 n (/ (count item) 2)
                                                 [a b] result]
                                             [(concat a (take n item))
                                              (concat b (drop n item))]))
                                         [() ()]
                                         weight-groups)
          weights (read-floats weights 4)
          bone-indices (read-floats bone-indices 4)
          lines (nthrest lines (inc n))
          inverse-bind-pose-matrices (read-floats
                                       (take (* num-bones 4) lines) 16)
          lines (nthrest lines (+ (* num-bones 4) 1))
          bone-matrices (read-floats lines 16)]
      {:num-bones num-bones
       :num-frames (/ (count bone-matrices) num-bones)
       :weights weights
       :bone-indices bone-indices
       :inverse-bind-pose-matrices inverse-bind-pose-matrices
       :bone-matrices bone-matrices})))

(defn create-animated-mesh-helper [vertices position rotation
                                   scale colors normals
                                   weights bone-indices]
  (let [scale (if (vector? scale)
                scale
                (vec (repeat 3 scale)))
        vertices (vec (flatten vertices))
        normals (if (empty? normals)
                  (vec (compute-normals vertices))
                  (vec (flatten normals)))
        colors (vec (flatten colors))
        weights (vec (flatten weights))
        bone-indices (vec (flatten bone-indices))]
    {:vertices vertices
     :vertices-buffer (get-float-buffer vertices)
     :normals-buffer (get-float-buffer normals)
     :weights-buffer (get-float-buffer weights)
     :bone-indices-buffer (get-float-buffer bone-indices)
     :position position
     :rotation rotation
     :scale scale
     :colors-buffer (get-float-buffer colors)
     :draw-fn draw-animated-mesh!
     :program :animated}))

(defn invert-matrix [m]
  (vec (matrix/get-inverse (float-array m))))

(defn create-animated-mesh [filename position rotation scale]
  (with-open [reader (clojure.java.io/reader filename)]
    (let [materials (parse-materials (change-extension filename "mtl"))
          lines (filter (fn [line]
                          (or (.startsWith line "o")
                              (.startsWith line "v")
                              (.startsWith line "vn")
                              (.startsWith line "vt")
                              (.startsWith line "f")
                              (.startsWith line "usemtl")))
                        (line-seq reader))
          names (map (fn [n]
                       (let [index (.lastIndexOf n "_")]
                         (if (neg? index)
                           (subs n 2)
                           (subs n 2 index))))
                     (filter #(.startsWith % "o ") lines))
          animation (parse-animation (change-extension filename "anim")
                                     names)
          v (map parse-line (filter #(.startsWith % "v ") lines))
          n (map parse-line (filter #(.startsWith % "vn") lines))
          t (map parse-line (filter #(.startsWith % "vt") lines))
          faces (mapcat parse-line-with-slashes
                        (filter #(.startsWith % "f") lines))
          vertices (use-indices v (map first faces))
          normals (use-indices n (map last faces))
          colors (create-colors lines materials)
          weights (use-indices (:weights animation) (map first faces))
          bone-indices (use-indices (:bone-indices animation)
                                    (map first faces))
          mesh (create-animated-mesh-helper vertices position
                                            rotation scale
                                            colors normals
                                            weights bone-indices)
          convert-matrices (fn [f matrices]
                             (float-array (flatten
                                            (map f matrices))))]
      (-> mesh
          (assoc-in [:index] 0)
          (assoc-in [:num-frames] (:num-frames animation))
          (assoc-in [:inverse-bind-pose-matrices]
                    (convert-matrices
                      (comp invert-matrix blender->opengl)
                      (:inverse-bind-pose-matrices animation)))
          (assoc-in [:bone-matrices]
                    (vec (map (partial convert-matrices blender->opengl)
                              (partition
                                (:num-bones animation)
                                (:bone-matrices animation)))))))))
