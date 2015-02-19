(ns carpaccio2.core
    (:require [reagent.core :as reagent :refer [atom]]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [goog.string :as gstring]
              [goog.string.format])
    (:import goog.History))

;; Set *print-fn* to console.log
(enable-console-print!)

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply gstring/format fmt args))

;; -------------------------
;; State
(defonce app-state
  (atom {
         :orders [{:id 1 :date "2014-11-02" :items 1 :price 2 :state "TAS"}]
         :last-order-id 1}))
(defn get-state [k & [default]]
  (clojure.core/get @app-state k default))
(defn put! [k v] (swap! app-state assoc k v))

(defn add-order[order]
  (let [order-id (inc (get-state :last-order-id))
        order (assoc order :id order-id)]
    (put! :last-order-id order-id)
    (put! :orders
          (conj (get-state :orders) order))))
(defn delete-order[order]
  (put! :orders
        (remove #(= order %) (get-state :orders))))

(def discounts
  {"VIC" 0.05
   "NSW" 0.06
   "ACT" 0.01
   "TAS" 0.03
   "NT"  0.01
   "SA"  0.04
   "QLD" 0.04
   "WA"  0.03})

;; -------------------------
;; Util

#_(defn resource [r]
    (-> (Thread/currentThread)
        (.getContextClassLoader)
        (.getResource r)
        slurp))

;; -------------------------
;; Views

(defmulti page identity)

(defn create-order[]
  {
   :date  (-> js/document .-order .-date .-value)
   :items (js/parseInt (-> js/document .-order .-items .-value))
   :price (js/parseFloat (-> js/document .-order .-price .-value))
   :state (-> js/document .-order .-state .-value)
   })

(defn order-total[order]
  (let [total (* (:items order)
                 (:price order))]
    (-  total
        (* total
           (discounts (:state order))))))

(defn order-partial[order]
  [:tr
   [:td (:date order)]
   [:td (format "%1.0f" (:items order))]
   [:td (format "$%1.2f" (:price order))]
   [:td (:state order)]
   [:td "%" (* 100.0 (discounts (:state order)))]
   [:td (format "$%1.2f" (order-total order))]
   [:td [:a.glyphicon.glyphicon-trash
         {:href "#" :on-click #(delete-order order)} ]]])

(defn form-element[id label element]
  [:div.form-group
   [:label.control-label.col-sm-2 {:for id} label]
   [:div.col-xs-8 element]])

(defn order-form[orders]
  [:div
   [:form.form-horizontal {:name "order"
                           :on-submit #(do (add-order (create-order))
                                           false)}
    (form-element "date" "Date:"
                  [:input.form-control {:type :date
                                        :id :date
                                        :required "required"}])
    (form-element "items" "Items:"
                  [:input.form-control {:type :numeric
                                        :id :items
                                        :pattern "^[1-9][0-9]*$"
                                        :required "required"
                                        :min 1 :max 1000000}])
    (form-element "price" "Price:"
                  [:input.form-control {:type :numeric
                                        :id :price
                                        :pattern "^\\$?(([1-9](\\d*|\\d{0,2}(,\\d{3})*))|0)(\\.\\d{1,2})?$"
                                        :required "required"
                                        :min 0 :max 1000000}])
    (form-element "state" "State:"
                  [:select.form-control {:id :state :required "required"}
                   [:option "VIC"]
                   [:option "NSW"]
                   [:option "TAS"]
                   [:option "QLD"]
                   [:option "ACT"]
                   [:option "NT"]
                   [:option "SA"]
                   [:option "WA"]
                   ])
    [:div.form-group
     [:div.col-sm-offset-2.col-sm-10
      [:input.btn.btn-primary {:type "submit" :value "Add"}]]]]])

(defn order-list[orders]
  [:table {:border "1" :style {:width "100%"}}
   [:tbody
    ^{:key "header-row"}
    [:tr#header-row
     [:th "Date"]
     [:th "Items"]
     [:th "Price"]
     [:th "State"]
     [:th "Discount"]
     [:th "Order Value"]]
    (for [order orders] ^{:key (:id order)} [order-partial order])
    ^{:key "total-row"}
    (when (not (zero? (count orders)))
      [:tr#total-row
       [:td]
       [:td (format "%1.0f" (reduce + (map (comp js/parseFloat :items) orders)))]
       [:td (format "$%1.2f" (reduce + (map (comp js/parseFloat :price) orders)))]
       [:td]
       [:td]
       [:td (format "$%1.2f" (reduce + (map (comp js/parseFloat order-total) orders)))]])]])

(defmethod page :page1 [_]
  [:div.container
   [:div.page-header
    [:h1 "Shop"]
    [:p.lead "Basic carpaccio slicing excercise example using Bootstrap and Clojure Reagent."]]
   [:h2 "Orders"]
   [order-list (get-state :orders)]
   [:hr]
   [order-form (get-state :orders)]])

(defmethod page :default [_]
  [:div "Invalid/Unknown route"])

(defn main-page []
  [:div [page (get-state :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (put! :current-page :page1))

(secretary/defroute "/page2" []
  (put! :current-page :page2))

;; -------------------------
;; Initialize app
(defn init! []
  (reagent/render-component [main-page] (.getElementById js/document "app")))

;; -------------------------
;; History
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))
;; need to run this after routes have been defined
(hook-browser-navigation!)
