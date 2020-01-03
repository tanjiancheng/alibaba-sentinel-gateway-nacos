# alibaba-sentinel-gateway-nacos

## 阿里sentinel-gateway网关（动态规则从nacos读取），基于版本v1.7.0改造

### 动态路由配置内容
```
[
    {
        "id": "be",
        "predicates": [
            {
                "name": "Path",
                "args": {
                    "pattern": "/be/**"
                }
            }
        ],
        "uri": "http://10.11.82.186:9501/",
        "filters": []
    },
    {
        "id": "rewrite",
        "predicates": [
            {
                "name": "Path",
                "args": {
                    "pattern": "/rewrite/**"
                }
            }
        ],
        "uri": "http://www.baidu.com/",
        "filters": [
            {
                "name": "RewritePath",
                "args" : {
                    "regexp": "/rewrite/(?<segment>.*)",
                    "replacement": "/$\\{segment}"
                }
            }
        ]
    }
]
```