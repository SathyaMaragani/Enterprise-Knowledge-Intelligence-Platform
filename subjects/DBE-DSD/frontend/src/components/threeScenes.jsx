// Loaded lazily by ThreeBackdrop. Subpath imports keep the bundle to the two
// threeui components actually used, rather than the whole catalog.
import '@designcodeio/threeui/style.css';
import { PortalFieldCollection } from '@designcodeio/threeui/components/PortalFieldCollection';
import { StructureFlowCollection } from '@designcodeio/threeui/components/StructureFlowCollection';

const SCENES = {
  // Night sky with drifting violet cloud ridges: the dusk backdrop on sign-in.
  'cloud-field': () => <PortalFieldCollection variant="cloud-field" />,
  // Volumetric violet haze: the glow behind the dashboard hero illustration.
  nebula: () => <StructureFlowCollection variant="nebula" />,
};

export default function ThreeScene({ scene }) {
  const Scene = SCENES[scene];
  return Scene ? <Scene /> : null;
}
