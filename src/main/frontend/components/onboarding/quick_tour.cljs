(ns frontend.components.onboarding.quick-tour
  (:require [promesa.core :as p]
            [cljs-bean.core :as bean]
            [frontend.loader :refer [load]]
            [frontend.state :as state]
            [frontend.date :as date]
            [frontend.util :as util]
            [frontend.handler.route :as router-handler]
            [hiccups.runtime :as h]
            [dommy.core :as d]))

(defn js-load$
  [url]
  (p/create
    (fn [resolve]
      (load url resolve))))

(def JS_ROOT
  (if (= js/location.protocol "file:")
    "./js"
    "./static/js"))

(defn- load-base-assets$
  []
  (js-load$ (str JS_ROOT "/shepherd.min.js")))

(defn- make-skip-fns
  [^js jsTour]
  (let [^js el (js/document.createElement "button")]
    (.add (.-classList el) "cp__onboarding-skip-quick-tour")
    (set! (.-innerHTML el) (h/render-html [:span [:i.ti.ti-player-skip-forward] "Skip Quick Tour"]))
    (.addEventListener el "click" #(.cancel jsTour))
    [#(.appendChild js/document.body el)
     #(.removeChild js/document.body el)]))

(defn- open-left-sidebar! []
  (state/set-state! :ui/left-sidebar-open? true))

(defn- wait-target
  [fn-or-selector time]
  (p/let [action (if (string? fn-or-selector)
                   #(d/sel1 fn-or-selector)
                   fn-or-selector)
          _      (action)
          _      (p/delay time)]))

(defn- create-steps! [^js jsTour]
  [
   ;; step 1
   {:id                "nav-help"
    :text              (h/render-html [:section [:h2 "❓  Help"]
                                       [:p "You can always click here for help and other information about Logseq."]])
    :attachTo          {:element ".cp__sidebar-help-btn" :on "top"}
    :beforeShowPromise #(if (state/sub :ui/sidebar-open?)
                          (wait-target state/hide-right-sidebar! 700)
                          (p/resolved true))
    :canClickTarget    true
    :buttons           [{:text "Next" :action (.-next jsTour)}]
    :popperOptions     {:modifiers [{:name    "preventOverflow"
                                     :options {:padding 20}}
                                    {:name    "offset"
                                     :options {:offset [0, 10]}}]}}

   ;; step 2
   {:id                "nav-journal-page"
    :text              (h/render-html [:section [:h2 "📆 Daily Journal Page"]
                                       [:p
                                        [:span "This is today’s daily journal page. Here yo can dump your thoughts, learnings and ideas. Don’t worry about organizing. Just write and"]
                                        [:a "[[link]]"]
                                        [:span "your thoughts."]]])

    :attachTo          {:element ".page.is-journals .page-title" :on "right"}
    :beforeShowPromise #(if-not (= (util/safe-lower-case (state/get-current-page))
                                  (util/safe-lower-case (date/today)))
                          (wait-target (fn []
                                         (router-handler/redirect-to-page! (date/today))
                                         (util/scroll-to-top)) 200)
                          (p/resolved true))
    :buttons           [{:text "Back" :classes "back" :action (.-back jsTour)}
                        {:text "Next" :action (.-next jsTour)}]
    :popperOptions     {:modifiers [{:name    "preventOverflow"
                                     :options {:padding 63}}
                                    {:name    "offset"
                                     :options {:offset [10, 10]}}]}}

   ;; step 3
   {:id                "nav-left-sidebar"
    :text              (h/render-html [:section [:h2 "👀 Left Sidebar"]
                                       [:p [:span "Open the left sidebar to explore important menu items in Logseq."]]])

    :attachTo          {:element "#left-menu" :on "top"}
    :beforeShowPromise #(p/resolved true)
    :buttons           [{:text "Back" :classes "back" :action (.-back jsTour)}
                        {:text "Next" :action (.-next jsTour)}]
    :popperOptions     {:modifiers [{:name    "preventOverflow"
                                     :options {:padding 20}}
                                    {:name    "offset"
                                     :options {:offset [10, 10]}}]}}

   ;; step 4
   {:id                "nav-favorites"
    :text              (h/render-html [:section [:h2 "⭐️ Favorites"]
                                       [:p "Pin your favorite pages via the `... `menu on any page."]
                                       [:p "We’ve also added some template pages here to help you get started. You can remove these once you start writing your own notes."]])
    :beforeShowPromise #(if-not (state/sub :ui/left-sidebar-open?)
                          (wait-target state/toggle-left-sidebar! 500)
                          (p/resolved true))
    :attachTo          {:element ".nav-content-item.favorites" :on "right"}
    :buttons           [{:text "Back" :classes "back" :action (.-back jsTour)}
                        {:text "Finish" :action (.-complete jsTour)}]}
   ])

(defn start
  []
  (let [^js jsTour (js/Shepherd.Tour.
                     (bean/->js
                       {:useModalOverlay    true
                        :defaultStepOptions {:classes  "cp__onboarding-quick-tour"
                                             :scrollTo false}}))
        steps      (create-steps! jsTour)
        [show-skip! hide-skip!] (make-skip-fns jsTour)]

    ;; events
    (doto jsTour
      (.on "show" show-skip!)
      (.on "hide" hide-skip!)
      (.on "complete" hide-skip!)
      (.on "cancel" hide-skip!))

    (doseq [step steps]
      (.addStep jsTour (bean/->js step)))

    (.start jsTour)))

(def should-guide? true)

(defn init []
  (prn "[debug] hello onboarding quick tour :)")

  (when should-guide?
    (p/then (load-base-assets$) start)))