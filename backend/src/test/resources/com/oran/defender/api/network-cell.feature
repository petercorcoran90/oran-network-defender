Feature: Network Cell API

Background:
  * url karate.properties['karate.baseUrl']

Scenario: list network cells for the seeded session
  Given path 'api', 'sessions', 1, 'cells'
  When method get
  Then status 200
  And match response == '#[4]'
  And match response[*].cellName contains 'Cell-A'
  And match response[*].cellName contains 'Cell-B'
  And match response[*].healthStatus contains 'WARNING'
  And match response[*].healthStatus contains 'GOOD'

Scenario: read a single network cell from the seeded session
  Given path 'api', 'sessions', 1, 'cells', 1
  When method get
  Then status 200
  And match response.id == 1
  And match response.cellName == 'Cell-A'
  And match response.healthStatus == 'WARNING'

Scenario: return not found for a missing network cell
  Given path 'api', 'sessions', 1, 'cells', 9999
  When method get
  Then status 404
