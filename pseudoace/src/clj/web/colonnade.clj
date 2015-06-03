(ns web.colonnade
  (:use hiccup.core
        pseudoace.utils
        ring.middleware.anti-forgery)   ;; for *anti-forgery-token*
  (:require [datomic.api :as d :refer (history q touch entity)]
            [clojure.string :as str]
            [ring.adapter.jetty :refer (run-jetty)]
            [compojure.core :refer (defroutes GET POST routes)]
            [compojure.route :as route]
            [compojure.handler :as handler]))

(defn- page []
  (html
   [:html
    [:head
     [:link {:rel "stylesheet"
             :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.0/css/bootstrap.min.css"}]
     [:link {:rel "stylesheet"
             :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.0/css/bootstrap-theme.min.css"}]
     [:link {:rel "stylesheet"
             :href "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css"}]
     [:link {:rel "stylesheet"
             :href "/css/trace.css"}]]
    [:body
     [:div.root
      [:div.header
       [:img.banner {:src "/img/logo_wormbase_gradient_small.png"}]
       [:h1#page-title "Colonnade"]
       [:img.banner {:src "/img/kazannevsky.jpg" :width 130 :style "float: right"}]
       [:div#header-content]]
     [:div.container-fluid
      [:div#table-maker]]]
     [:script {:src "/js/out/goog/base.js"
               :type "text/javascript"}]
     [:script {:src "/js/main.js"
               :type "text/javascript"}]
     [:script {:type "text/javascript"}
      (str "trace_token = '" *anti-forgery-token* "';")
      "goog.require('trace.colonnade');"]]]))


(defn- starts-with [^String s ^String p]
  (.startsWith s p))

(def ^:private prefix-num-re #"([A-Za-z_-]*)(\d+)")

(defn prefix-num-compare
  "If x and y are strings containing numbers, possibly with a common
   alphabetic prefix, compare the numbers.  Otherwise, compare as
   if with `compare`."
  [x y]
  (or (and (string? x)
           (string? y)
           (if-let [[_ px nx] (re-matches prefix-num-re x)]
             (if-let [[_ py ny] (re-matches prefix-num-re y)]
               (and (= px py)
                    (compare (parse-int nx)
                             (parse-int ny))))))
      (compare x y)))
               

(defn post-query [db {:keys [query rules args drop-rows max-rows timeout]}]
  (let [args (if (seq rules)
               (cons rules args)
               args)
        results (d/query
                 {:query query
                  :args (cons db args)
                  :timeout (or timeout 5000)})]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (pr-str {:query query
                    :results (cond->> (sort-by first prefix-num-compare results)
                                      drop-rows    (drop drop-rows)
                                      max-rows     (take max-rows))
                    :count (count results)})}))

(defn colonnade [db]
  (routes
   (GET "/" [] (page))
   (POST "/query" {edn-params :edn-params} (post-query db edn-params))))
