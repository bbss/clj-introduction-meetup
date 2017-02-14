(ns sounds-network.cljs.core
  (:require
   #_[om.core :as om :include-macros true]
   [sablono.core :as sab :include-macros true]
   [datascript.core :as d]
   [clojure.spec.impl.gen :as gen]
   [clojure.spec :as s]
   [cljs.core.async :as async :refer [chan close!]]
   [ajax.core :refer [GET]]
   [dirac.runtime]
   [devtools.core :as devtools])
  (:require-macros
   [devcards.core :as dc :refer [defcard deftest]]
   [cljs.core.async.macros :refer [go alt!]]))

(dirac.runtime/install!)
(devtools/install!)

(enable-console-print!)

(defonce es (js/EventSource. "/new-users"))

(.addEventListener es "message" (fn [e] (js/console.log
                                         (:korean-person/surname (cljs.reader/read-string (.-data e))))))

(defcard first-card
  (sab/html [:div
             [:h1 "This is your first devcard!"]]))

(defn main []
  ;; conditionally start the app based on whether the #main-app-area
  ;; node is on the page
  (if-let [node (.getElementById js/document "main-app-area")]
    (.render js/ReactDOM (sab/html [:div "This is working"]) node)))

(main)

;; remember to run lein figwheel and then browse to
;; http://localhost:3449/cards.html
