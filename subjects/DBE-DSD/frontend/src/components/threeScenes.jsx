// Loaded lazily by ThreeBackdrop. The subpath import keeps the bundle to the one
// threeui component actually used, rather than the whole catalog.
import '@designcodeio/threeui/style.css';
import { PortalFieldCollection } from '@designcodeio/threeui/components/PortalFieldCollection';

const SCENES = {
  // Night sky with drifting violet cloud ridges: the dusk backdrop on sign-in.
  'cloud-field': () => <PortalFieldCollection variant="cloud-field" />,
};

export default function ThreeScene({ scene }) {
  const Scene = SCENES[scene];
  return Scene ? <Scene /> : null;
}
