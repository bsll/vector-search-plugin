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
    "range": {
      "pca_reduced_vector": {
        "from": "0,0,0,0,0,0,0,0",
        "to": "5,5,5,5,5,5,5,5"
      }
    }
  },
  "rescore": {
    "window_size": 10000,
    "query": {
      "rescore_query": {
        "function_score": {
          "boost_mode": "replace",
          "script_score": {
            "script": {
              "inline": "binary_vector_score",
              "lang": "knn",
              "params": {
                "cosine": false,
                "field": "full_vector",
                "vector": [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 12, 13, 14, 15]
              }
            }
          }
        }
      }
    }
  },
  "size": 75
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
plugin mentioned above required Base64 encoded binary values of floating point numbers, see their documentation.

It's also possible to combine this plugin with other approaches, the FAISS library implements a few suggestions that
work with elasticsearch, such as clustering (just index and filter on cluster labels).

Good luck, send me a message if you have questions!
