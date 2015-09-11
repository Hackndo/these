from flask import Flask, render_template, Response, request, make_response, jsonify
import redis
import json
import time
import ast

app = Flask(__name__)
r = redis.StrictRedis(host='127.0.0.1', port=6379, db=0)
minute_review = 100

@app.route('/users/total')
def mongo_get_users_total():
    current_time = int(time.time()*1000)
    # We get the last minute_review of users
    resp = r.zrangebyscore("users", current_time - minute_review*60*1000, current_time, withscores=True)

    # We delete messages older than minute_review
    r.zremrangebyscore("users", 0, current_time - minute_review*60*1000 - 1)

    # Response building
    resp = {'current_time': str(current_time), 
        'current_time_minus_' + str(minute_review): str(current_time - minute_review*60*1000), 
        'response': len(resp),
        'details': resp
    }
    return render_template('dashboard.html', response=resp)

@app.route('/users/components')
def mongo_get_users_per_component():
    current_time = int(time.time()*1000)
    # We get the last minute_review of users
    resp = r.zrangebyscore("users_per_component", current_time - minute_review*60*1000, current_time)
    app.logger.info("[RESP] " + str(resp))
    c = {}
    for re in resp:
        app.logger.info("[RE] " + str(re))
        d = ast.literal_eval(re)
        if d["component"] not in c:
            c[d["component"]] = 1
        else:
            c[d["component"]] += 1
    app.logger.info("[ROM] " + str(c))
    # We delete messages older than minute_review
    app.logger.info(r.zremrangebyscore("users_per_component", 0, current_time - minute_review*60*1000 - 1))

    # Response building
    resp = {'current_time': str(current_time), 
        'current_time_minus_' + str(minute_review): str(current_time - minute_review*60*1000), 
        'response': len(resp),
        'modules': c
    }
    return render_template('dashboard_comp.html', response=resp)
    #return jsonify(**resp)

@app.route('/users/current')
def mongo_get_users_current():
    current_time = int(time.time()*1000)
    # We get the last minute_review of users
    resp = r.zrangebyscore("user_last_component", current_time - minute_review*60*1000, current_time, withscores=True)
    app.logger.info("[RESP] " + str(resp))
    c = {}
    u = {}
    for re in resp:
        app.logger.info("[RE] " + str(re))
        d = ast.literal_eval(re[0])
        u[d["user_id"]] = d["component"]

    for user, component in u.items():
        if component not in c:
            c[component] = 1
        else:
            c[component] += 1
    app.logger.info("[ROM] " + str(c))
    # We delete messages older than minute_review
    app.logger.info(r.zremrangebyscore("users_per_component", 0, current_time - minute_review*60*1000 - 1))

    # Response building
    resp = {'current_time': str(current_time), 
        'current_time_minus_' + str(minute_review): str(current_time - minute_review*60*1000), 
        'response': len(resp),
        'modules': c
    }
    return render_template('dashboard_comp.html', response=resp)
    #return jsonify(**resp)


if __name__ == '__main__':
    app.run(threaded=True,
        debug=True,
        host='0.0.0.0',
        port=5000
    )