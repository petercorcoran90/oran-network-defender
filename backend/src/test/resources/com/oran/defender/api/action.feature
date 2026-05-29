Feature: Action API

Background:
  * url karate.properties['karate.baseUrl']

Scenario: list the seeded remediation action catalog
  Given path 'api', 'actions'
  When method get
  Then status 200
  And match response == '#[9]'
  And match response[*].actionName contains 'REBALANCE_TRAFFIC'
  And match response[*].actionName contains 'IGNORE'
  And match response[0].displayName == 'Rebalance Traffic'
  And match response[0].remediationCost == 10
