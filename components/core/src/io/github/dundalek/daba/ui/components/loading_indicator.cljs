(ns io.github.dundalek.daba.ui.components.loading-indicator
  (:require
   [portal.colors :as c]
   [portal.ui.styled :as s]
   [portal.ui.theme :as theme]
   [portal.viewer :as-alias pv]))

(defn loading-indicator []
  (let [theme (theme/use-theme)]
    [s/div {:style {:position "relative"
                    :overflow "hidden"
                    :height 3
                    :margin-top -9
                    :margin-bottom 6
                    :background (::c/background2 theme)}}
     [s/div {:style {:position "absolute"
                     :height "100%"
                     :width "40%"
                     :animation "io-github-dundalek-daba-loading-animation 1s linear infinite"
                     :background (::c/tag theme)}}]]))
