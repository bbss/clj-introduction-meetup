(ns sounds-network.clj.core
  (:require [jsoup.soup :as jsoup :refer [text $ get!]]
            [datascript.core :as d]
            [clojure.spec.gen :as gen]
            [clojure.spec :as s]
            [clojure.pprint :refer [pprint]]
            [yada.yada :as yada :refer [yada]]
            [clojure.core.match :refer [match]]
            [clojure.core.async :refer [mult chan <! >! >!! <!! timeout go put! take!]]
            ))

;;show object DS

(def first-user
  {:user/given-name "Baruch"
   :user/last-name "Berger"
   :user/follows []})

;;add impression for this website has community, not because I am lonely.
;;start with given names, pick popular ones because reliable
;;so I will invent my own follows on this social network
;;they will be Koreans!

(def surnames
  (jsoup/$
   (jsoup/get! "https://en.wiktionary.org/wiki/Appendix:Korean_surnames")
   "#mw-content-text > table > tbody > tr > td:nth-child(1)"))

(def surname-count
  (jsoup/$
                    (jsoup/get! "https://en.wiktionary.org/wiki/Appendix:Korean_surnames")
                            "#mw-content-text > table > tbody > tr > td:nth-child(6)"))

(def surname-count-tuples
  (->> (map vector
               (->>
                surnames
                jsoup/text
                (map #(clojure.string/trim
                       (first (clojure.string/split % #"\(S\)")))))

               (->>
                surname-count
                jsoup/text
                (map (fn [s] (clojure.string/replace s #"," "")))
                (map (fn [no] (Integer/parseInt no)))
                ))
          (map first
               (filter
                (fn [[name count]]
                  (when (> count 100000)
                    name))
                ))))

(-> (get! "https://en.wikipedia.org/wiki/List_of_the_most_popular_given_names_in_South_Korea")
    ($ "#mw-content-text > table > tbody > tr > td:nth-child(4)")
    text
    )

(s/def :korean-person/surname
  (->>
   surnames
   jsoup/text
   (map #(clojure.string/trim
          (first (clojure.string/split % #"\(S\)"))))
   set))

(s/def :korean-person/given-name
  (-> (get! "https://en.wikipedia.org/wiki/List_of_the_most_popular_given_names_in_South_Korea")
      ($ "#mw-content-text > table > tbody > tr > td:nth-child(4)")
      text
      set))

(s/def ::korean-person
  (s/keys :req [:korean-person/given-name :korean-person/surname]
          :opt [:korean-person/follows]))

(s/def :korean-person/follows
  (s/cat :follows (s/+ ::korean-person)))

(def sample (-> (gen/sample (s/gen ::korean-person) 2)))
(def sample (-> (gen/sample (s/gen ::korean-person) 26)))

(defn make-negative [number]
  (if (> number 0)
    (unchecked-negate number)
    number))

(def flattened (-> (reduce
                    (fn [all-users next-user]
                      ((fn follows-with-id [user]
                         (let [id (-> user
                                       (dissoc :korean-person/follows)
                                       hash
                                       make-negative
                                       )
                               mapped-follows (map follows-with-id
                                                   (:korean-person/follows user))
                               ided-user (-> user
                                             (assoc :korean-person/db/id id)
                                             (assoc :korean-person/follows (map (comp :korean-person/db/id :ided-user)
                                                                                mapped-follows)))]
                           {:all-users (clojure.set/union
                                        (conj (set (:all-users all-users)) ided-user)
                                        (reduce clojure.set/union
                                                (map :all-users mapped-follows)))
                            :ided-user ided-user}))
                       next-user))
                                #{}
                                sample)
                   :all-users
                   set))

(def conn (d/create-conn {:korean-person/follows {:db/cardinality :db.cardinality/many
                                                  :db/valueType :db.type/ref}}))

(d/transact! conn
             (map
              (fn [flatten-me]
                (clojure.set/rename-keys flatten-me
                                                        {:korean-person/db/id :db/id}))
                  flattened))

(d/q '[:find (pull ?f [*])
       :where
       [?e :korean-person/follows ?f]]
     @conn)

;;up sample size

(-> (d/q '[:find ?e
           :where
           [?e :korean-person/given-name]]
         @conn)
    count)

(d/q '[:find ?e ?f ?given-name-first ?given-name-second ?same-name ?sa
       :where
       [?e :korean-person/follows ?f]
       [?f :korean-person/follows ?e]
       [?f :korean-person/given-name ?given-name-first]
       [?e :korean-person/given-name ?given-name-second]
       [?f :korean-person/surname ?same-name]
       [?e :korean-person/surname ?sa]]
     @conn)
;; :)
;; => #{[1420 1420 "지호" "지호" "심" "심"]}

;; => #{[2711 3847 "경수" "현준" "임" "낭"] [5854 4003 "영희" "영기" "유" "편"] [4106 6623 "동현" "민규" "갈" "계"] [3847 2711 "현준" "경수" "낭" "임"] [379 379 "영환" "영환" "풍" "풍"] [4003 5854 "영기" "영희" "편" "유"] [6623 4106 "민규" "동현" "계" "갈"] [11382 11382 "하은" "하은" "오" "오"]}

;;put into datascript make queries like:
;; if putting in ds is easy, else no time

;;give me all follows with track in genre I like

(def routes
  [""
   [["/new-users" (yada/resource {:methods {:get {:produces "text/event-stream"
                                                                :response broadcast-incoming-users}}})]
    ["/cards.html" (-> (yada
                        (clojure.java.io/file "resources/public/cards.html"))
                       (assoc :id ::cards))]
    ["/build" [["/"
                (yada/handler (new java.io.File "resources/public/build"))]]]
    ["/js" [["/" (yada/handler (new java.io.File "resources/public/js"))]]]
    ["/css" [["/" (yada/handler (new java.io.File "resources/public/css"))]]]
    ["/" (-> (yada (clojure.java.io/file "resources/public/index.html"))
             (assoc :id ::index))]]])

(def listen (yada/listener routes {:port 3000}))

;;events user joins
;;events user follows
;;make listener for events
;;add transit
;;listen on cljs

(defn add-user [conn korean-person]
  (d/transact! conn
               [(merge {:db/id -1}
                       korean-person)])
  :k)

;;cljs devtools
(def persons (gen/sample (s/gen ::korean-person) 26))

(first (filter #(not (:korean-person/follows %)) persons))

(add-user conn
          (nth (filter #(not (:korean-person/follows %)) persons)
               1))

(d/listen! conn :test pprint)

(d/unlisten! conn :test)

(def user-addition
  [#datascript/Datom [11747 :korean-person/given-name "지우" 536870914 true]
   #datascript/Datom [11747 :korean-person/surname "복" 536870914 true]])

(defn is-user-addition? [transactions]
  (some (fn [transaction]
          (and (= (second transaction)) :korean-person/given-name
               (= (nth transaction 4) true)))
        transactions))

(defn get-user-from-addition [addition]
  {(-> addition
       (nth 0)
       (nth 1))
   (-> addition
       (nth 0)
       (nth 2))
   (-> addition
       (nth 1)
       (nth 1))
   (-> addition
       (nth 1)
       (nth 2))})

(get-user-from-addition user-addition)

(def incoming-users-channel
  (chan))

(def broadcast-incoming-users (mult incoming-users-channel))

(d/listen! conn :test
           (fn [{:keys [tx-data]}]
             (when (is-user-addition? tx-data)
               (let []
                 (println "sup doggo" (get-user-from-addition tx-data))
                 (put! incoming-users-channel
                          (get-user-from-addition tx-data)))
               )))
