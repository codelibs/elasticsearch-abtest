# elasticsearch-abtest

## Overview

AbTestPlugin provide simple A/B testing for search request. This plugin rewrite a search target index by [ab_rt] parameter automatically.

## Usage

### Setup test settings

```
curl -XPOST localhost:9201/sample-index/_abtest/settings -d '
{
  "testcases": [
    {
      "test_name": "test1",
      "index": "sample-index-a",
      "percentage": 10
    },
    {
      "test_name": "test2",
      "index": "sample-index-b",
      "percentage": 20
    }
  ]
}'
```

### Executing test

A search target index is rewritten by ab_rt parameter. In the above case, if ab_rt parameter is 0-9, target index is rewritten sample-index-a.

Search sample-index
```
curl -XPOST localhost:9200/sample-index/doc/_search?q=*:*
```

Search sample-index-a (ab_rt is 0-9)
```
curl -XPOST localhost:9200/sample-index/doc/_search?q=*:*&ab_rt=5&hash_rt=false
```

Search sample-index-b (ab_rt is 10-29)
```
curl -XPOST localhost:9200/sample-index/doc/_search?q=*:*&ab_rt=20&hash_rt=false
```

If you set "true" to hash_rt parameter, ab_rt parameter value is converted to hashCode.(default)
