package grails3.redis.session

import grails.plugin.springsecurity.annotation.Secured

class TestController {
  @Secured('ROLE_ADMIN')
  def index() { } // Renders `test/index.gsp`
}
