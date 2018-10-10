# Vector Search Plugin

Elasticsearch plugin for fast nearest neighbour search in high(er) dimensional data. This plugin can help do things
similar as the FAISS library with Elasticsearch.

The advantage of integrating this in Elasticsearch is that the vector similarity can then be part of your normal
query. Without this plugin you would need to first find nearest neighbours using another tool (e.g. FAISS) and then
put the IDs of the nearest neighbours in your elastic query. This is cumbersome and probably slower..

## Build & setup

In order to install this plugin, you need to build the distribution files first:

```
mvn clean package
```

This will produce a zip file in target/releases.

After building the zip file, you can install it like this:

```
elasticsearch-plugin install file:///path/to/plugin/FILE.zip
```

After this you are ready to go!


## Intended use case

We built this plugin to quickly find nearest neighbours of high dimensional vectors. This is usefull if you are using
an embeddings approach, such as word2vec or LSI or a custom Deep Learning method. For example if you would process all
your documents with Doc2Vec, the nearest neighbours of the resulting embedded vectors would give you similar documents.
Nearest neightbours of embedded queries could have an effect comparable to having synonym expansion.

This plugin uses the lucene KDTree data structure under to implement the fast nearest neighbours search, that data
structure works only for limited dimensional data (by default 8, you can re-compile lucene with higher values..) , so
we need to reduce dimensionality for example by preprocessing with PCA. Then you can issue a range query for e.g. the
range [-0.5 .. +0.5] to get the nearest neighbours in that reduced dimensional space and then re-score with the full
dimensional vectors for the actual nearest neighbours.

So this plugin is probably most usefull in combination with one of the vector distance plugins, https://github.com/muhleder/elasticsearch-vector-scoring

Example query:

```
POST my_index/_search
{
  "query": {
    "function_score": {
      "query": {
        "range": {
          "pca_reduced_vector": {
            "from": "-0.5,-0.5,-0.5,-0.5,-0.5,-0.5,-0.5,-0.5",
            "to": "0.5,0.5,0.5,0.5,0.5,0.5,0.5,0.5"
          }
        }
      },
      "functions": [
        {
          "script_score": {
            "script": {
              "inline": "vector_scoring",
              "lang": "binary_vector_score",
              "params": {
                "vector_field": "full_vector",
                "vector": [ 0.0, 0.0716, 0.1761, 0.0, 0.0779, 0.0, 0.1382, 0.3729 ]
              }
            }
          }
        }
      ],
      "boost_mode": "replace"
    }
  },
  "size": 10
}
```

The mapping could look like this:

```
PUT my_index
{
  "mappings": {
    "doc": {
      "properties": {
        "full_vector": {
          "type": "binary",
          "doc_values": true
        },
        "pca_reduced_vector": {
          "type": "vector",
          "dimensions": 8
        }
      }
    }
  }
}
```

For indexing, this plugin expects comma separated floating point values (without spaces in between). The vector scoring
plugin mentioned above required Base64 encoded binary values of floating point numbers, see their documentation. Example:

```
full_vector = [0.0, 0.0, ....]  # Full length vector
pca_reduced_vector = [0.0, 0.0, ....]  # 8 dimensional reduced vector

es.index(INDEX, DOC_TYPE, {
  "full_vector": base64.b64encode(np.array(full_vector).astype(np.dtype('>f8'))).decode("utf-8"),
  "pca_reduced_vector": ",".join([str(x) for x in pca_reduced_vector]),
})
```

It's also possible to combine this plugin with other approaches, the FAISS library implements a few suggestions that
work with elasticsearch, such as clustering (just index and filter on cluster labels).


## Speed / performance

Speed is one of the main reasons to use this plugin.  Let's say you want to find the 1000 nearest neighbours from a
dataset of 500.000 documents. Without any filtering, calculating all the distances using the vector scoring plugin
mentioned above takes around 1500ms on my laptop. With pre-filtering on the PCA reduced vectors, you get the same 1000
nearest neighbours, but it takes only 75ms. So that's around 20 times faster.

![Number of reranked documents vs Query time (milliseconds)](https://github.com/EikeDehling/vector-search-plugin/raw/master/size_vs_time.png "Number of docs versus Query time")

You will need to experiment to find an optimal range if you decide to use this plugin. There is a tradeoff between
accuracy and speed. The vectors as dimension reduced by PCA explain only part of the variance of the full vectors,
so if the filtering is too aggressive you lose some near neighbours. If filtering is too broad, you need to calculate
full distances on a lot of vectors, making things slow. For us a good point was to set the filtering/range such that we
rescore about 10.000 - 20.000 full vectors. That gave us almost perfect accuracy with good speed.

![Number of reranked documents vs Result quality (Overlap of top 100)](https://github.com/EikeDehling/vector-search-plugin/raw/master/size_vs_overlap.png "Number of docs versus Overlap")


## Enjoy!

Good luck, send me a message if you have questions!
