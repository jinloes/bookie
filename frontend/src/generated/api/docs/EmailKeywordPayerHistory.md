
# EmailKeywordPayerHistory


## Properties

Name | Type
------------ | -------------
`id` | number
`keyword` | string
`payer` | [Payer](Payer.md)
`occurrences` | number
`version` | number

## Example

```typescript
import type { EmailKeywordPayerHistory } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "keyword": null,
  "payer": null,
  "occurrences": null,
  "version": null,
} satisfies EmailKeywordPayerHistory

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as EmailKeywordPayerHistory
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


