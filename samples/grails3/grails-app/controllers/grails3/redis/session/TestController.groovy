package grails3.redis.session

class TestController {
  def index() {
    render "hello $session.id"
  }
}
