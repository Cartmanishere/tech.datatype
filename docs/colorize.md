# Colorizing


The images displayed of US surface temperatures where generated via colorizing
a tensor:

```clojure
(defn color-lerp
  ^BufferedImage [api-varname variable-data tile-size]
  (if-let [color-data (get default-api-variable-color-map api-varname)]
    (let [{:keys [min-value max-value gradient-name]} color-data]
      (when-not (= (dtype/ecount variable-data)
                   (* tile-size tile-size))
        (throw (Exception. "Variable data is wrong size.")))
      (-> (dtt/reshape variable-data [tile-size tile-size])
          (dtt-gradients/colorize gradient-name
                                  :data-min min-value
                                  :data-max max-value)))
    (throw (Exception. (format "Failed to find color data for var: %s" api-varname)))))
```

* [Gradient Demo](https://github.com/techascent/tech.datatype/blob/640733fa994f96dd3058e9ec83049d2efb3802c4/src/tech/v2/tensor/color_gradients.clj#L177)
* [Documentation for colorizing](https://github.com/techascent/tech.datatype/blob/640733fa994f96dd3058e9ec83049d2efb3802c4/src/tech/v2/tensor/color_gradients.clj#L134)
