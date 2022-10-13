# YouthSkills2022
第17回若年者ものづくり競技大会(業務用ITソフトウェアソリューションズ部門)出場に向けて、練習で作成したアプリケーションです。

<br>

## 概要
地図上にプロットされた基地局情報から、電波の提供状況を把握する。<br>
電波の提供状況の改善を図るためのツールとして、仮想基地局の追加ができるよう実装。

<br>

## データベース
### 使用データベース
- PostgreSQL
### テーブル
|列|タイプ|Null値を許容|概要|
|--|--|--|--|
|id|integer|not null|主キー|
|x|integer|not null|基地局のx座標|
|y|integer|not null|基地局のy座標|
|frequency|character varying(10)|not null|基地局の周波数|
|radio_strength|smallint|not null|基地局の電波強度|
|isVirtual|boolean|not null|仮想の基地局であるかどうか|

<br>
