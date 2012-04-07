(ns leiningen.generative
  (:refer-clojure :exclude [test])
  (:use [leiningen.test :only [*exit-after-tests*]]
        [leiningen.core.eval :only [eval-in-project]]
        [clojure.pprint :only [pprint]]
        [leiningen.classpath :only [classpath]]))


(defn- run-generative-tests [project]
  `(do
     (let [path# ~(or (:generative-path project) "test/")]
       (letfn [(print-line# [line-contents#]
                 (println (apply str (take 80 (cycle line-contents#)))))
               (report# [result#]
                 (if-let [err# (:error result#)]
                   (do
                     (println)
                     (print-line# "=")
                     (println "FAILURE")
                     (print-line# "=")
                     (println ":file     " (str path#
                                                java.io.File/separatorChar
                                                (-> result# :form first meta :file)))
                     (println ":spec     " (-> result# :form first meta :name))
                     (println)
                     (print ":form      ")
                     (prn (:form result#))
                     (println ":error    " err#)
                     (println)
                     (println ":seed     " (:seed result#))
                     (println ":iteration" (:iteration result#))
                     (print-line# "="))
                   (prn result#)))]
         (dosync (reset! gen/report-fn report#)))
       (println "Testing generative tests in" path#
                "on" gen/*cores* "cores for"
                gen/*msec* "msec.")
       (let [futures# (gen/test-dirs ~(:generative-path project))]
         (try (doseq [f# futures#]
                (try @f# (catch Throwable t#)))
           (finally
             (when ~*exit-after-tests*
               (shutdown-agents))))))))

(defn- set-generative-path-to-project [project]
  (let [generative-path (str (:root project)
                             java.io.File/separatorChar
                             "generative")]
    (merge {:generative-path generative-path} project)))

(defn- add-generative-path-to-classpath [project]
  (update-in project
             [:extra-classpath-dirs]
             #(conj % (:generative-path project))))

(defn generative
  "Run test.generative tests"
  [project & _]
  (let [new-project (-> project
                        set-generative-path-to-project
                        add-generative-path-to-classpath)]
    (eval-in-project
      new-project
      (run-generative-tests new-project)
      '(require '[clojure.test.generative :as gen]))))
