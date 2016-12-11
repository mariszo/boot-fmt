(set-env! :resource-paths #{"src" "dev"}
          :dependencies '[[zprint "0.2.9"]]
          :repositories
          (partial map (fn [[k v]] [k (cond-> v (#{"clojars"} k) (assoc :username (System/getenv "CLOJARS_USER"),
                                                                :password (System/getenv "CLOJARS_PASS")))])))

(require '[boot-fmt.core :refer [fmt]] '[boot-fmt.impl :as impl])

(task-options! pom
               {:project 'boot-fmt/boot-fmt,
                :description "Boot task to auto-format Clojure(Script) code",
                :url "https://github.com/pesterhazy/boot-fmt",
                :scm {:url "https://github.com/pesterhazy/boot-fmt"},
                :license {"Eclipse Public License"
                          "http://www.eclipse.org/legal/epl-v10.html"}}

               fmt
               {:mode :diff
                :options {:fn-map {":require" :force-nl-body, "ns" :arg1-body},
                          :style :community,
                          :fn-force-nl #{:force-nl :noarg1 :noarg1-body
                                         :force-nl-body :binding}}})

(defn get-version []
  (read-string (slurp "release.edn")))

(defn format-version [{:keys [version]}]
  (clojure.string/join "." version))

;!zprint {:format :skip}
(deftask dogfood
  "Reformat this very repository"
  [m mode MODE kw "mode"
   r really bool "really"]
  (fmt :mode (or mode :diff)
       :source true
       :really really))

(defn replace-help [outer inner]
  (clojure.string/replace outer
                          #"(?m)(?s)(<!-- begin help -->)(.*)(<!-- end help -->)$"
                          (fn [[_ a b c]] (str a "\n\n```\n" inner "\n```\n" c))))

(deftask update-help []
  (with-pre-wrap fileset
    (->> (replace-help (slurp "README.md") (with-out-str (boot.core/boot "fmt" "-h")))
        (spit "README.md"))
    fileset))

(deftask bump
  []
  (spit "release.edn" (update-in (get-version) [:version 2] inc)))

(deftask build [] (comp (pom :version (format-version (get-version))) (jar) (install)))

(deftask deploy
         []
         (comp (build)
               (push :repo
                     "clojars"
                     :gpg-sign
                     false)))

(deftask example
         []
         (println "Reading from stdin...\n")
         (let [code (slurp *in*)]
           (impl/example code)))
