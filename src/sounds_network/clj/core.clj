(ns sounds-network.clj.core
  (:require [jsoup.soup :as jsoup :refer [text $ get!]]
            [datascript.core :as d]
            [clojure.spec.gen :as gen]
            [clojure.spec :as s]
            [clojure.pprint :refer [pprint]]
            [yada.yada :as yada :refer [yada]]
            [clojure.core.match :refer [match]]
            [clojure.core.async :refer [mult chan <! >! >!! <!! timeout
                                        go put! take!]]
            ))

;;clojure syntax is small
;;to call a function in c-like languages you prefix the function
;;doSomething(someValue, someOtherValue)
;;in clojure this is infixed:
;;(do-something some-value some-other-value)













;;typical object {}
(def typical-object
  {:user {:given-name "Baruch"
          :follows []
          }})

(get typical-object :user)
(:user typical-object)

;;get when nested deeply
(get-in typical-object [:user :given-name])

;;add a new attribute
(assoc-in typical-object [:user :last-name] "Berger")

;;immutable

;;define to actually change (mutate) object
(def me-with-last-name
  (assoc-in typical-object [:user :last-name] "Berger"))
;; => #'sounds-network.clj.core/me-with-last-name
;; => #'sounds-network.clj.core/me-with-last-name

;;update list, even if attribute didn't exist conj
;;will create a list with the addition
(update-in
 me-with-last-name
 [:user :follows]
 (fn [following] (conj following "interesting-person")))


;;with namespaced keywords
;;let's say I am the first user:
{:user/given-name "Baruch"
 :user/last-name "Berger"
 :user/follows []}

;;atom gives a way to manage state change
(def application-state (atom {}))

(swap! application-state update :users (fn [users] (conj users first-user)))

;;add impression for this website has community, I have nobody to follow!
;;start with given names, pick popular ones to be realistic
;;then
;;they will be Koreans!

(def surname-url
  "https://en.wiktionary.org/wiki/Appendix:Korean_surnames")

;;use the java library jsoup to grab the page
(def surname-page
  (jsoup/get! surname-url))
surname-page
;;and the correct html element
(def surnames
  (jsoup/text
   (jsoup/$ surname-page
            "#mw-content-text > table > tbody > tr > td:nth-child(1)")))

;;also get the amount of people that have that name
(def surname-count
  (jsoup/$
   surname-page
   "#mw-content-text > table > tbody > tr > td:nth-child(6)"))

;;combine the surnames and surname count and filter out the popular ones
(def popular-surnames
  (->> (map vector
            (->>
             surnames
             (map #(clojure.string/trim
                    (first (clojure.string/split % #"\(S\)")))))

            (->>
             surname-count
             jsoup/text
             (map (fn [s] (clojure.string/replace s #"," "")))
             (map (fn [no] (Integer/parseInt no)))
             ))
       (filter
        (fn [[name count]]
          (when (> count 100000)
            name))
        )
       (map first)))

;;use clojure spec to say "korean-person/surname is one of this set of names"
(s/def :korean-person/surname
  (set popular-surnames))


;;do the same for first names
(def given-names
  (-> (get! "https://en.wikipedia.org/wiki/List_of_the_most_popular_given_names_in_South_Korea")
      ($ "#mw-content-text > table > tbody > tr > td:nth-child(4)")
      text
      set))

(s/def :korean-person/given-name
  given-names)


;;say a korean person is someone with a given name and surname and possibly follows one or more other korean persons
(s/def ::korean-person
  (s/keys :req [:korean-person/given-name :korean-person/surname]
          :opt [:korean-person/follows]))

;;follows spec, compare to regex
(s/def :korean-person/follows
  (s/cat :follows (s/+ ::korean-person)))

;;can use for validation
(s/valid? ::korean-person #:korean-person{:given-name "민서", :surname "권"})


;;and generation
(-> (gen/sample (s/gen ::korean-person) 1))



;;we will use clojure hashes to give an id to a person data object and make
;;and that hash will be made a negative number so datascript can use them as references

(def sample (-> (gen/sample (s/gen ::korean-person) 26)))

(defn make-negative [number]
  (if (> number 0)
    (unchecked-negate number)
    number))

;;take the list with persons and turn them from a tree datastructure into
;;a flat database structure, takes a while because clojure is lazy and starts generating only when needed

(def flattened
  (-> (reduce
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

;;create a datascript "connection" with a many reference to followers
(def conn (d/create-conn
           {:korean-person/follows {:db/cardinality :db.cardinality/many

                                    :db/valueType :db.type/ref}}))

;;fill the database with the flattened generated data
(d/transact! conn
             (map
              (fn [flatten-me]
                (clojure.set/rename-keys flatten-me
                                         {:korean-person/db/id :db/id}))
              flattened))

;;find persons that follow someone
(count (d/q '[:find ?anyone
              :where
              [?f :korean-person/surname "성"]
              [?f :korean-person/follows ?anyone]]
            @conn))

(-> (d/q '[:find ?e
           :where
           [?e :korean-person/given-name]]
         @conn)
    count)

;;find people who follow eachother!
(d/q '[:find ?e ?f ?given-name-first ?given-name-second ?same-name ?sa
       :where
       [?e :korean-person/follows ?f]
       [?f :korean-person/follows ?e]
       [?f :korean-person/given-name ?given-name-first]
       [?e :korean-person/given-name ?given-name-second]
       [?f :korean-person/surname ?same-name]
       [?e :korean-person/surname ?sa]]
     @conn)

;;create a function to add a person to the db
(defn add-user [conn korean-person]
  (d/transact! conn
               [(merge {:db/id -1}
                       korean-person)])
  nil)

;;make user follow another
(defn follow-user [conn follower followed]
  (d/transact! conn
               [[:db/add follower :koreanp-person/follows followed]]))

;;generate some test persons to add/make follow
(def persons (gen/sample (s/gen ::korean-person) 26))

;;test adding a user
(add-user conn
          (nth (filter #(not (:korean-person/follows %)) persons)
               1))

;;transaction looks like this
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

;;create a channel for sending new users asynchronously
(def incoming-users-channel
  (chan))

;;turn this channel into a mult, so more consumers can receive the new users
(def broadcast-incoming-users (mult incoming-users-channel))

;;create routes for the server
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
;;start a server, shut it off by evaluating ((:close listen))
(def listen (yada/listener routes {:port 3000}))

;;create a listener that puts new users on the incoming users channel
(d/listen! conn :new-users
           (fn [{:keys [tx-data]}]
             (when (is-user-addition? tx-data)
               (let []
                 (println "new user added!" (get-user-from-addition tx-data))
                 (put! incoming-users-channel
                       (get-user-from-addition tx-data)))
               )))
