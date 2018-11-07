(def fn-0 (fn [] (println "No args call!")))
(if true (println "Hello\nWorld!"))
(if null
  (println "you don't see me")
  (println (prn-str (quote (println "I'm quoted!")))))
(let [blarg println
      println quote
      fn-1 (fn [arg]
               (blarg "Passed argument: " (prn-str arg))
               arg)]
     (blarg (println (let works !)))
     (fn-0)
     (fn-1 "Just passing by."))
((fn [do if quote let fn]
     (println do if quote let fn))
  1 2 3 4 5)
(def multi
  (fn ([] (println "Nothing to see."))
      ([x] (println "Something to see: " x))))
(multi)
(multi "FooBar!")
(println (#{:test} :test))
(println ({:key :value} :key))