
# PendingExpenseResponse


## Properties

Name | Type
------------ | -------------
`id` | number
`sourceId` | string
`sourceType` | string
`emailType` | string
`subject` | string
`status` | string
`amount` | number
`description` | string
`date` | Date
`category` | string
`propertyName` | string
`payerName` | string
`errorMessage` | string
`createdAt` | Date

## Example

```typescript
import type { PendingExpenseResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "sourceId": null,
  "sourceType": null,
  "emailType": null,
  "subject": null,
  "status": null,
  "amount": null,
  "description": null,
  "date": null,
  "category": null,
  "propertyName": null,
  "payerName": null,
  "errorMessage": null,
  "createdAt": null,
} satisfies PendingExpenseResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as PendingExpenseResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


