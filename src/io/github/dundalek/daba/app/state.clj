(ns io.github.dundalek.daba.app.state)

(def default-state
  {::next-cell-id 1
   ::cells (sorted-map-by >)})
