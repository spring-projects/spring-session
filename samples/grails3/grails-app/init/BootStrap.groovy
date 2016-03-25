import grails3.redis.session.*

class BootStrap {

    def init = { servletContext ->
      def adminRole = new Role('ROLE_ADMIN').save()
      def userRole = new Role('ROLE_USER').save()

      def testUser = new User('user', 'password').save()

      UserRole.create testUser, adminRole

      UserRole.withSession {
         it.flush()
         it.clear()
      }

      assert User.count() == 1
      assert Role.count() == 2
      assert UserRole.count() == 1
    }
    def destroy = {
    }
}
