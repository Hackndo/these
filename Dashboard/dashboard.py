from flask import Flask, render_template, Response, request, make_response
import redis
import json
import time

app = Flask(__name__)
r = redis.StrictRedis(host='127.0.0.1', port=6379, db=0)
minute_review = 1

@app.route('/users')
def mongo_get_user():
    current_time = int(time.time()*1000)
    # We get the last minute_review of users
    resp = r.zrangebyscore("storm", current_time - minute_review*60*1000, current_time, withscores=True)

    # We delete messages older than minute_review
    r.zremrangebyscore("storm", 0, current_time - minute_review*60*1000 - 1)

    # Response building
    resp = {'current_time': str(current_time), 
        'current_time_minus_' + str(minute_review): str(current_time - minute_review*60*1000), 
        'response': len(resp),
        'details': resp
    }
    return render_template('dashboard.html', response=resp)


if __name__ == '__main__':
    app.run(threaded=True,
        debug=True,
        host='0.0.0.0'
)


