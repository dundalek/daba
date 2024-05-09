(ns io.github.dundalek.daba.app.state)

(def default-state
  (with-meta
    {::next-cell-id 1
     ::cells (sorted-map-by >)}
    {:portal.viewer/default :daba.viewer/root}))
