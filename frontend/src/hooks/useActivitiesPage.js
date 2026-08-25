import { useQuery, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import {
  createFinancialActivity,
  createHouseholdMember,
  getFinancialActivities,
  getFinancialActivityTypes,
  getHouseholdMembers,
  getTaxTreatments,
  updateFinancialActivity,
  updateHouseholdMember,
} from '../api/index.js';
import { queryKeys } from '../queryKeys.js';

export function useActivitiesPage() {
  const queryClient = useQueryClient();
  const activitiesQuery = useQuery({
    queryKey: queryKeys.financialActivities,
    queryFn: getFinancialActivities,
  });
  const membersQuery = useQuery({
    queryKey: queryKeys.householdMembers,
    queryFn: getHouseholdMembers,
  });
  const activityTypesQuery = useQuery({
    queryKey: queryKeys.financialActivityTypes,
    queryFn: getFinancialActivityTypes,
  });
  const taxTreatmentsQuery = useQuery({
    queryKey: queryKeys.taxTreatments,
    queryFn: getTaxTreatments,
  });

  const saveActivity = async (id, data) => {
    const saved = id
      ? await updateFinancialActivity(id, data)
      : await createFinancialActivity(data);
    await queryClient.invalidateQueries({ queryKey: queryKeys.financialActivities });
    await queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
    await queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
    await queryClient.invalidateQueries({ queryKey: ['reports'] });
    notifications.show({
      title: id ? 'Activity updated' : 'Activity saved',
      message: `${saved.name} is ready to use for transactions.`,
      color: 'green',
    });
    return saved;
  };

  const saveMember = async (id, data) => {
    const saved = id ? await updateHouseholdMember(id, data) : await createHouseholdMember(data);
    await queryClient.invalidateQueries({ queryKey: queryKeys.householdMembers });
    await queryClient.invalidateQueries({ queryKey: queryKeys.financialActivities });
    await queryClient.invalidateQueries({ queryKey: queryKeys.incomes });
    await queryClient.invalidateQueries({ queryKey: queryKeys.expenses });
    await queryClient.invalidateQueries({ queryKey: ['reports'] });
    notifications.show({
      title: id ? 'Household member updated' : 'Household member saved',
      message: `${saved.name} is available as an activity owner.`,
      color: 'green',
    });
    return saved;
  };

  return {
    activities: activitiesQuery.data ?? [],
    members: membersQuery.data ?? [],
    activityTypes: activityTypesQuery.data ?? [],
    taxTreatments: taxTreatmentsQuery.data ?? [],
    isLoading:
      activitiesQuery.isLoading ||
      membersQuery.isLoading ||
      activityTypesQuery.isLoading ||
      taxTreatmentsQuery.isLoading,
    saveActivity,
    saveMember,
  };
}
