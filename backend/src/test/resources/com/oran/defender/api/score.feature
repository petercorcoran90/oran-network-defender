Feature: Score API

Background:
  * url karate.properties['karate.baseUrl']

Scenario: read the seeded session scoreboard
  Given path 'api', 'sessions', 1, 'scores'
  When method get
  Then status 200
  And match response == '#[2]'
  And match each response[*].score == 0
  And match response[*].username contains 'alice'
  And match response[*].username contains 'bob'

Scenario: read score events for a session without events
  Given path 'api', 'sessions', 1, 'scores', 'events'
  When method get
  Then status 200
  And match response == []
