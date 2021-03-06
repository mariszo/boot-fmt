(ns boot-fmt.impl
 (:require [zprint.core :as zp]
           [zprint.config :as zc]
           [zprint.zprint :as zprint]
           [zprint.zutil :as zutil]
           [rewrite-clj.parser :as p])
 (:import [com.google.common.io Files]))

(defn zprint-whole
  [wholefile file-name]
  ;; FIXME
  ;; Copy 'n paste job from zprint.core
  (let [lines (clojure.string/split wholefile #"\n")
        lines (if (:expand? (:tab (zc/get-options)))
                (map (partial zprint/expand-tabs
                              (:size (:tab (zc/get-options))))
                     lines)
                lines)
        filestring (clojure.string/join "\n" lines)
        filestring
        (if (= (last wholefile) \newline) (str filestring "\n") filestring)
        forms (zutil/edn* (p/parse-string-all filestring))]
    (zprint.core/process-multiple-forms {:process-bang-zprint? true}
                                        zprint.core/zprint-str-internal
                                        (str "file: " file-name)
                                        forms)))


(defn transform [contents file-name] (zprint-whole contents file-name))

(defn mangle
  [file-name nam]
  (let [basename (-> (clojure.string/split file-name #"/")
                     last)
        mangled (clojure.string/replace-first basename
                                              #"(\.[^.]*$)"
                                              (str "." nam "$1"))]
    (if (= mangled basename) (str basename "." nam) mangled)))

(defn diff
  [old-file-name new-file-name old-content new-content]
  (let [tempdir (Files/createTempDir)]
    (try (let [old-f (java.io.File. tempdir old-file-name)
               new-f (java.io.File. tempdir new-file-name)]
           (spit old-f old-content)
           (spit new-f new-content)
           (-> (clojure.java.shell/sh "git"
                                      "diff"
                                      "--no-index"
                                      "--color"
                                      (.getAbsolutePath old-f)
                                      (.getAbsolutePath new-f))
               :out
               println))
         (finally (doseq [file (.listFiles tempdir)] (.delete file))
                  (.delete tempdir)))))

(defn example
  [old-content]
  (let [new-content (transform old-content "old")]
    (diff "old" "new" old-content new-content)))

(defmulti act (fn [opts params] (:mode opts)))

(defmethod act :print
  [opts {:keys [file old-content new-content]}]
  (println new-content))

(defmethod act :diff
  [opts {:keys [file old-content new-content]}]
  (diff (mangle (.getName file) "old")
        (mangle (.getName file) "new")
        old-content
        new-content))

(defmethod act :list
  [opts {:keys [old-content new-content file]}]
  (when (not= old-content new-content)
    (println "File changed:" (.getPath file))))

(defmethod act :overwrite
  [opts {:keys [old-content new-content file]}]
  (when (not= old-content new-content)
    (println "Overwriting file:" (.getPath file))
    (spit file new-content)))

(defn process
  [file {:keys [mode], :as info}]
  (let [old-content (slurp file)
        new-content (transform old-content (.getName file))]
    (act info {:file file, :old-content old-content, :new-content new-content})
    {:file file, :changed? (not= old-content new-content)}))

(defn process-many
  [{:keys [zprint-options], :as opts} files]
  (zprint.core/set-options! zprint-options)
  (when-not (seq files) (throw (RuntimeException. "No files found")))
  (doseq [file files] (process file opts)))

(defn process-many-file-names
  [opts file-names]
  (process-many opts (map #(java.io.File. %) file-names)))
