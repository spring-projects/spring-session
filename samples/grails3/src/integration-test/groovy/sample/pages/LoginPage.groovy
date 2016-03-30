/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.pages

import geb.*

/**
 * The Login Page
 *
 * @author Rob Winch
 */
class LoginPage extends Page {
  static url = '/login'
  static at = { assert driver.title == 'Login'; true}
  static content = {
    form { $('form') }
    submit { $('input[type=submit]') }
    login(required:false) { user='user', pass='password' ->
      form.username = user
      form.password = pass
      submit.click()
    }
  }
}
