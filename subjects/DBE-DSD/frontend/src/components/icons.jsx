// Inline stroke icons on a 24px grid. A handful of paths is lighter than an icon
// dependency, and every icon inherits color and size from CSS.

function Icon({ children, size = 20, className, ...rest }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      className={className}
      {...rest}
    >
      {children}
    </svg>
  );
}

export const SearchIcon = (p) => (
  <Icon {...p}>
    <circle cx="11" cy="11" r="7" />
    <path d="M20.5 20.5 16 16" />
  </Icon>
);

export const FileTextIcon = (p) => (
  <Icon {...p}>
    <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" />
    <path d="M14 3v5h5M9 13h6M9 17h6" />
  </Icon>
);

export const UsersIcon = (p) => (
  <Icon {...p}>
    <circle cx="9" cy="8" r="3.5" />
    <path d="M3 20a6 6 0 0 1 12 0M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.4a6 6 0 0 1 3 5.6" />
  </Icon>
);

export const BarChartIcon = (p) => (
  <Icon {...p}>
    <path d="M5 20v-8M12 20V5M19 20v-5" />
  </Icon>
);

export const HomeIcon = (p) => (
  <Icon {...p}>
    <path d="M3 11 12 4l9 7" />
    <path d="M5.5 9.5V20h13V9.5M10 20v-5h4v5" />
  </Icon>
);

export const TagIcon = (p) => (
  <Icon {...p}>
    <path d="M3 12.2V4h8.2l9.3 9.3-8.2 8.2z" />
    <circle cx="7.6" cy="8.4" r="1.2" />
  </Icon>
);

export const ShieldIcon = (p) => (
  <Icon {...p}>
    <path d="M12 3 20 6v6c0 4.8-3.4 8-8 9-4.6-1-8-4.2-8-9V6z" />
    <path d="m9 12 2 2 4-4" />
  </Icon>
);

export const UploadIcon = (p) => (
  <Icon {...p}>
    <path d="M12 15V4M7 9l5-5 5 5M5 20h14" />
  </Icon>
);

export const UserIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21a8 8 0 0 1 16 0" />
  </Icon>
);

export const LockIcon = (p) => (
  <Icon {...p}>
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V7a4 4 0 0 1 8 0v4" />
  </Icon>
);

export const EyeIcon = (p) => (
  <Icon {...p}>
    <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" />
    <circle cx="12" cy="12" r="3" />
  </Icon>
);

export const EyeOffIcon = (p) => (
  <Icon {...p}>
    <path d="m3 3 18 18M10.6 5.1Q11.3 5 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3.2 4.1M6.6 6.6C3.9 8.3 2 12 2 12s3.6 7 10 7a10 10 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2" />
  </Icon>
);

export const ArrowRightIcon = (p) => (
  <Icon {...p}>
    <path d="M5 12h14M13 6l6 6-6 6" />
  </Icon>
);

export const BuildingIcon = (p) => (
  <Icon {...p}>
    <rect x="4" y="3" width="16" height="18" rx="1.5" />
    <path d="M9 7h1M14 7h1M9 11h1M14 11h1M9 15h1M14 15h1M10.5 21v-3h3v3" />
  </Icon>
);

export const SunIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </Icon>
);

export const ChevronDownIcon = (p) => (
  <Icon {...p}>
    <path d="m6 9 6 6 6-6" />
  </Icon>
);

export const LogOutIcon = (p) => (
  <Icon {...p}>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
  </Icon>
);

export const SparkleIcon = (p) => (
  <Icon {...p}>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6.3 6.3l2.5 2.5M15.2 15.2l2.5 2.5M6.3 17.7l2.5-2.5M15.2 8.8l2.5-2.5" />
    <circle cx="12" cy="12" r="1.6" />
  </Icon>
);

export const CubeIcon = (p) => (
  <Icon {...p}>
    <path d="M12 3 20 7.5v9L12 21l-8-4.5v-9z" />
    <path d="m4 7.5 8 4.5 8-4.5M12 12v9" />
  </Icon>
);

export const TypeIcon = (p) => (
  <Icon {...p}>
    <path d="M4 7V5h16v2M9 19h6M12 5v14" />
  </Icon>
);

export const LayersIcon = (p) => (
  <Icon {...p}>
    <path d="m12 3 9 5-9 5-9-5z" />
    <path d="m3 13 9 5 9-5" />
  </Icon>
);

export const DatabaseIcon = (p) => (
  <Icon {...p}>
    <ellipse cx="12" cy="5.5" rx="8" ry="2.8" />
    <path d="M4 5.5v6.2c0 1.6 3.6 2.8 8 2.8s8-1.2 8-2.8V5.5M4 11.7v6.5c0 1.5 3.6 2.8 8 2.8s8-1.3 8-2.8v-6.5" />
  </Icon>
);

export const CheckCircleIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="m8 12.2 2.8 2.8L16 9.5" />
  </Icon>
);

export const PencilIcon = (p) => (
  <Icon {...p}>
    <path d="M4 20h4L19 9l-4-4L4 16z" />
    <path d="m13.5 6.5 4 4" />
  </Icon>
);

export const PlusIcon = (p) => (
  <Icon {...p}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);

export const AlertIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7.5v5.5M12 16.5h.01" />
  </Icon>
);

export const WavesIcon = (p) => (
  <Icon {...p}>
    <path d="M3 9c2-2.5 4-2.5 6 0s4 2.5 6 0 4-2.5 6 0M3 15c2-2.5 4-2.5 6 0s4 2.5 6 0 4-2.5 6 0" />
  </Icon>
);

export const SlidersIcon = (p) => (
  <Icon {...p}>
    <path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0" />
    <circle cx="16" cy="6" r="2" />
    <circle cx="10" cy="12" r="2" />
    <circle cx="18" cy="18" r="2" />
  </Icon>
);

export const XIcon = (p) => (
  <Icon {...p}>
    <path d="M6 6l12 12M18 6 6 18" />
  </Icon>
);
