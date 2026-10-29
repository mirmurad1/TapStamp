import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { usePlan, type Feature } from '@/shared/plan/plan';
import { Tabs } from '@/shared/ui/tabs';

import { InlineLoading } from '../../components/FullPageState';
import { BonusRulesPage } from '../bonus/BonusRulesPage';
import { CampaignsPage, StampCampaignsPage } from './CampaignsPage';

const TABS: readonly { value: 'bonus' | 'sms' | 'stamp'; feature?: Feature }[] = [
  { value: 'bonus', feature: 'BONUS_RULES' },
  { value: 'sms' },
  { value: 'stamp', feature: 'STAMP_CAMPAIGNS' },
];
type HubTab = (typeof TABS)[number]['value'];

/**
 * Kampaniyalar bir bölmədə: bonus, SMS və möhür kampaniyaları tablarda. Paketdə olmayan imkanın tabı göstərilmir
 * (backend yenə özü yoxlayır). Seçilmiş tab ünvanda saxlanılır (?tab=...), köhnə keçidlər də bura yönləndirilir.
 */
export function CampaignHubPage() {
  const { t } = useTranslation('admin');
  const plan = usePlan();
  const [params, setParams] = useSearchParams();
  if (!plan.data) {
    return <InlineLoading />;
  }
  const features = plan.data.features ?? [];
  const available = TABS.filter((tab) => !tab.feature || features.includes(tab.feature));
  const requested = params.get('tab');
  const tab: HubTab = available.find((item) => item.value === requested)?.value ?? available[0]?.value ?? 'sms';
  return (
    <div className="flex flex-col gap-6">
      {available.length > 1 ? (
        <Tabs
          value={tab}
          onChange={(value) => setParams({ tab: value }, { replace: true })}
          label={t('nav.campaigns')}
          options={available.map((item) => ({ value: item.value, label: t(`campaignHub.tabs.${item.value}`) }))}
        />
      ) : null}
      {tab === 'bonus' ? <BonusRulesPage /> : null}
      {tab === 'sms' ? <CampaignsPage /> : null}
      {tab === 'stamp' ? <StampCampaignsPage /> : null}
    </div>
  );
}
