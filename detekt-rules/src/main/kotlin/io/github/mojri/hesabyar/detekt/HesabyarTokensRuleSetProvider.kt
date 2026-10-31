package io.github.mojri.hesabyar.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class HesabyarTokensRuleSetProvider : RuleSetProvider {
  override val ruleSetId: RuleSetId = RuleSetId("HesabyarTokens")

  override fun instance(): RuleSet {
    return RuleSet(
      ruleSetId,
      mapOf(
        RuleName("RawColorOutsideTheme") to { config: Config ->
          RawColorOutsideTheme(config)
        },
        RuleName("RawDpOutsideTheme") to { config: Config ->
          RawDpOutsideTheme(config)
        },
      ),
    )
  }
}
