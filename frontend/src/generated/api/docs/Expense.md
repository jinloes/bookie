
# Expense


## Properties

Name | Type
------------ | -------------
`id` | number
`amount` | number
`description` | string
`date` | Date
`category` | string
`property` | [Property](Property.md)
`sourceType` | string
`sourceId` | string
`payer` | [Payer](Payer.md)
`receiptOneDriveId` | string
`receiptFileName` | string

## Example

```typescript
import type { Expense } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "amount": null,
  "description": null,
  "date": null,
  "category": null,
  "property": null,
  "sourceType": null,
  "sourceId": null,
  "payer": null,
  "receiptOneDriveId": null,
  "receiptFileName": null,
} satisfies Expense

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Expense
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


