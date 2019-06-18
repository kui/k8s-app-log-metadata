fluentd-kubernetes-daemonset にアプリケーションログも対応させる
==============================================================

tl;dr ダメだった。現状は見づらくても複数行ログを1行にして標準出力するのが手っ取り早そう。


やりたいこと
---------------

アプリケーションログを JSON でファイル出力し、 fluentd-kubernetes-daemonset にそのファイルを食わせたい。

これにより、標準出力以外のアプリケーションログにも kubenetes メタ情報が付与される。

このリポジトリでやりたいことを「できるだけ」やったので以下が再現手順:

```
$ eval $(minikube docker-env) # ビルドした docker image を minikube に上げるためのコマンド
$ docker build -t log-demo log-demo
$ kubectl -k .
```

適用が終わるまで待つ

```
$ kubectl logs --follow --namespace kube-system fluentd-shwfh # daemonset の name は実行環境ごとに違うので確認すること
2019-06-18 10:12:44.393970145 +0000 kubernetes.var.log.containers.log-demo-ff9cc9745-t6tbd_default_log-demo-82fb60c1c220c0b4a43446feb9805e3fa15cd5d9b654ca95bf0501513258e220.log: {"log":"This is stdout #6473\n","stream":"stdout","docker":{"container_id":"82fb60c1c220c0b4a43446feb9805e3fa15cd5d9b654ca95bf0501513258e220"},"kubernetes":{"container_name":"log-demo","namespace_name":"default","pod_name":"log-demo-ff9cc9745-t6tbd","container_image":"log-demo:latest","container_image_id":"docker://sha256:629fa60d2b80de5c8aca653c8f1473eacaecbad239d7ac8c97158bc92b54021b","pod_id":"8306ce27-91a2-11e9-9fdc-0800275d76ff","labels":{"app":"log-demo","pod-template-hash":"ff9cc9745"},"host":"minikube","master_url":"https://10.96.0.1:443/api","namespace_id":"3991071a-880f-11e9-9fdc-0800275d76ff"}}
2019-06-18 10:12:44.393000000 +0000 app.var.log.app.log-demo-ff9cc9745-t6tbd_default_log-demo_app.log: {"level":"ERROR","thread":"main","logger":"kui.demo.log.LogDemoCommandLineRunner","message":"This is error #6473","context":"default","docker":{"container_id":""},"kubernetes":{"container_name":"log-demo","namespace_name":"default","pod_name":"log-demo-ff9cc9745-t6tbd","pod_id":"8306ce27-91a2-11e9-9fdc-0800275d76ff","labels":{"app":"log-demo","pod-template-hash":"ff9cc9745"},"host":"minikube","master_url":"https://10.96.0.1:443/api","namespace_id":"3991071a-880f-11e9-9fdc-0800275d76ff"}}
...
```

一応それらしいことは出来たが片手落ちになってる。

kube-log-record-example.json (fluentd-kubernetes-daemonset が最初から対応してる stdout/stderr の fluent record の一例): 

```
{
  "log": "This is stderr #1545\n",
  "stream": "stderr",
  "docker": {
    "container_id": "82fb60c1c220c0b4a43446feb9805e3fa15cd5d9b654ca95bf0501513258e220"
  },
  "kubernetes": {
    "container_name": "log-demo",
    "namespace_name": "default",
    "pod_name": "log-demo-ff9cc9745-t6tbd",
    "container_image": "log-demo:latest",
    "container_image_id": "docker://sha256:629fa60d2b80de5c8aca653c8f1473eacaecbad239d7ac8c97158bc92b54021b",
    "pod_id": "8306ce27-91a2-11e9-9fdc-0800275d76ff",
    "labels": {
      "app": "log-demo",
      "pod-template-hash": "ff9cc9745"
    },
    "host": "minikube",
    "master_url": "https://10.96.0.1:443/api",
    "namespace_id": "3991071a-880f-11e9-9fdc-0800275d76ff"
  }
}
```

app-log-record-example.json (今回やりたかったこと、fluentd-kubernetes-daemonset 食わせたアプリケーションログの一例):

```
{
  "level": "WARN",
  "thread": "main",
  "logger": "kui.demo.log.LogDemoCommandLineRunner",
  "message": "This is warn #1546",
  "context": "default",
  "docker": {
    "container_id": ""
  },
  "kubernetes": {
    "container_name": "log-demo",
    "namespace_name": "default",
    "pod_name": "log-demo-ff9cc9745-t6tbd",
    "pod_id": "8306ce27-91a2-11e9-9fdc-0800275d76ff",
    "labels": {
      "app": "log-demo",
      "pod-template-hash": "ff9cc9745"
    },
    "host": "minikube",
    "master_url": "https://10.96.0.1:443/api",
    "namespace_id": "3991071a-880f-11e9-9fdc-0800275d76ff"
  }
}
```

このように `docker.container_id` とそこから取れる `kubernetes.container_image`, `kubernetes.container_image_id` が掛けている。

これは Downward API が Container ID の取得に対応していないため fluentd-kubernetes-daemonset とそのコアのプラグインの fluent-plugin-kubernetes_metadata_filter がそこから関連情報を取得できないため。

* [how to get the container id in pod? · Issue #50309 · kubernetes/kubernetes](https://github.com/kubernetes/kubernetes/issues/50309)

[conf/app.conf](conf/app.conf) ではその Container ID を与えないと filter の実行自体止まってしまうため空文字列を与えている。

しかし Conteiner ID は、Kubenetes API を叩いた結果のキャッシュのキーに使われているため、空文字列を与えるとキャッシュが効かずログ1つごとにリクエストが飛ぶことになってしまう。

このため、このアプローチは失敗に終わった。

以下は代用の手段について考察している。


標準出力に出せばよいのでは？
-------------------------

標準出力にアプリケーションログを出してしまうと、複数行のログが切り刻まれてしまう。

出力時に改行コードを置換しても良いが見づらくなる。


標準出力にアプリケーションログを1行の JSON で出力しては？
--------------------------------------------------

JSON とそうじゃないものが混ざるので、 fluentd の設定で JSON としてパースしてしまうとエラーハンドリングが必要になる

fluentd でのエラーハンドリングで複雑になる覚悟があるならこれでいいかも。
