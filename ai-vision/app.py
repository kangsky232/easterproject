from flask import Flask, jsonify, request

app = Flask(__name__)


@app.route("/api/vision/verify", methods=["POST"])
def verify():
    # TODO: 调用 YOLOv8 进行火焰/烟雾检测
    return jsonify({"code": 0, "message": "success", "data": None})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
