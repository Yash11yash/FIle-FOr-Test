using UnityEngine;

[RequireComponent(typeof(Rigidbody), typeof(CapsuleCollider))]
public class PlayerMovementController : MonoBehaviour
{
    [System.Serializable]
    private struct MovementSettings
    {
        public float walkSpeed;
        public float runSpeed;
        public float crouchSpeed;
        public float rotationSpeed;
        public float jumpForce;
        public float groundCheckDistance;
    }

    [System.Serializable]
    private struct VaultSettings
    {
        public float vaultCheckDistance;
        public float maxVaultHeight;
        public float minVaultHeight;
        public float vaultSpeed;
        public LayerMask vaultObstacleMask;
        public LayerMask vaultLandingMask;
    }

    [System.Serializable]
    private struct KeyBinds
    {
        public KeyCode sprintKey;
        public KeyCode crouchKey;
        public KeyCode jumpKey;
        public KeyCode attackKey;
    }

    // Component references
    [SerializeField] private Transform cameraTransform;
    [SerializeField] private Animator characterAnimator;
    [SerializeField] private LayerMask groundMask;

    [Header("Movement Settings")]
    [SerializeField]
    private MovementSettings settings = new MovementSettings
    {
        walkSpeed = 4f,
        runSpeed = 8f,
        crouchSpeed = 2f,
        rotationSpeed = 10f,
        jumpForce = 5f,
        groundCheckDistance = 0.2f
    };

    [Header("Vault Settings")]
    [SerializeField]
    private VaultSettings vaultSettings = new VaultSettings
    {
        vaultCheckDistance = 0.5f,
        maxVaultHeight = 1.5f,
        minVaultHeight = 0.5f,
        vaultSpeed = 5f,
        vaultObstacleMask = 1,
        vaultLandingMask = 1
    };

    [Header("Key Bindings")]
    [SerializeField]
    private KeyBinds keyBinds = new KeyBinds
    {
        sprintKey = KeyCode.LeftShift,
        crouchKey = KeyCode.LeftControl,
        jumpKey = KeyCode.Space,
        attackKey = KeyCode.Mouse0
    };

    // Internal components
    private Rigidbody rb;
    private CapsuleCollider col;

    // State variables
    private bool isGrounded;
    private bool isJumping;
    private bool isFalling;
    private bool isVaulting;
    private bool isCrouching;
    private bool isInCombat;
    private Vector3 moveDirection;
    private float jumpInputBufferTimer;
    private float crouchSTimer;
    private float attackCooldownTimer;
    private int currentAttackIndex; // 0 = RPunch, 1 = LPunch, 2 = Kick
    private const float JUMP_BUFFER_TIME = 0.2f;
    private const float CROUCH_S_DURATION = 3f;
    private const float RPUNCH_COOLDOWN = 0.97f; // 29/30 frames at 30 FPS
    private const float LPUNCH_COOLDOWN = 0.97f; // 29/30 frames at 30 FPS
    private const float KICK_COOLDOWN = 1.63f;  // 1s + 19/49 frames at 30 FPS

    // Vaulting variables
    private Vector3 vaultStartPosition;
    private Vector3 vaultEndPosition;
    private float vaultLerpTime;
    private float vaultTotalTime;

    // Animation parameters
    private const string ANIM_WALK = "IsWalking";
    private const string ANIM_RUN = "IsRunning";
    private const string ANIM_JUMP = "IsJumping";
    private const string ANIM_FALL = "IsFalling";
    private const string ANIM_GROUNDED = "IsGrounded";
    private const string ANIM_VAULT = "IsVaulting";
    private const string ANIM_VAULT_TRIGGER = "VaultTrigger";
    private const string ANIM_CROUCH_MODE = "CrouchMode";
    private const string ANIM_CROUCH_P = "CrouchP";
    private const string ANIM_CROUCH_S = "CrouchS";
    private const string ANIM_COMBAT = "Combat";
    private const string ANIM_RPUNCH = "RPunch";
    private const string ANIM_LPUNCH = "LPunch";
    private const string ANIM_KICK = "Kick";

    private void Awake()
    {
        InitializeComponents();
        SetupDefaultValues();
    }

    private void Update()
    {
        if (isVaulting) return;

        UpdateGroundCheck();
        UpdateJumpInput();
        UpdateCrouchInput();
        UpdateCombatInput();
        CheckForVault();

        // Handle timers
        if (crouchSTimer > 0)
        {
            crouchSTimer -= Time.deltaTime;
            if (crouchSTimer <= 0)
            {
                characterAnimator.SetBool(ANIM_CROUCH_S, false);
            }
        }

        if (attackCooldownTimer > 0)
        {
            attackCooldownTimer -= Time.deltaTime;
            if (attackCooldownTimer <= 0)
            {
                // Reset current attack animation but keep Combat true
                characterAnimator.SetBool(ANIM_RPUNCH, false);
                characterAnimator.SetBool(ANIM_LPUNCH, false);
                characterAnimator.SetBool(ANIM_KICK, false);
            }
        }
    }

    private void FixedUpdate()
    {
        if (isVaulting)
        {
            ProcessVault();
        }
        else if (!isInCombat)
        {
            ProcessMovement();
        }
    }

    private void InitializeComponents()
    {
        rb = GetComponent<Rigidbody>();
        rb.freezeRotation = true;
        col = GetComponent<CapsuleCollider>();
    }

    private void SetupDefaultValues()
    {
        if (cameraTransform == null && Camera.main != null)
            cameraTransform = Camera.main.transform;

        if (groundMask == 0)
            groundMask = LayerMask.GetMask("Default");

        if (vaultSettings.vaultObstacleMask == 0)
            vaultSettings.vaultObstacleMask = LayerMask.GetMask("Default");

        if (vaultSettings.vaultLandingMask == 0)
            vaultSettings.vaultLandingMask = LayerMask.GetMask("Default");

        ResetAllAnimatorParameters();
    }

    private void ResetAllAnimatorParameters()
    {
        if (characterAnimator == null) return;

        characterAnimator.SetBool(ANIM_WALK, false);
        characterAnimator.SetBool(ANIM_RUN, false);
        characterAnimator.SetBool(ANIM_JUMP, false);
        characterAnimator.SetBool(ANIM_FALL, false);
        characterAnimator.SetBool(ANIM_GROUNDED, true);
        characterAnimator.SetBool(ANIM_VAULT, false);
        characterAnimator.SetBool(ANIM_CROUCH_MODE, false);
        characterAnimator.SetBool(ANIM_CROUCH_P, false);
        characterAnimator.SetBool(ANIM_CROUCH_S, false);
        characterAnimator.SetBool(ANIM_COMBAT, false);
        characterAnimator.SetBool(ANIM_RPUNCH, false);
        characterAnimator.SetBool(ANIM_LPUNCH, false);
        characterAnimator.SetBool(ANIM_KICK, false);
        characterAnimator.ResetTrigger(ANIM_VAULT_TRIGGER);
    }

    private void UpdateGroundCheck()
    {
        Vector3 checkPosition = transform.position + col.center - Vector3.up * (col.height / 2 - col.radius);
        isGrounded = Physics.SphereCast(
            checkPosition,
            col.radius * 0.9f,
            Vector3.down,
            out _,
            settings.groundCheckDistance,
            groundMask
        );

        isFalling = !isGrounded && !isJumping && !isVaulting;

        if (isGrounded && isJumping)
        {
            isJumping = false;
            isFalling = false;
            UpdateAnimationStates(false, false, false, false, true);
        }
        else
        {
            bool walking = characterAnimator.GetBool(ANIM_WALK);
            bool running = characterAnimator.GetBool(ANIM_RUN);
            UpdateAnimationStates(walking, running, isJumping, isFalling, isGrounded);
        }
    }

    private void UpdateJumpInput()
    {
        if (Input.GetKeyDown(keyBinds.jumpKey))
        {
            if (isInCombat)
            {
                ExitCombat();
            }
            jumpInputBufferTimer = JUMP_BUFFER_TIME;
        }

        if (jumpInputBufferTimer > 0)
        {
            jumpInputBufferTimer -= Time.deltaTime;
            if (isGrounded && !isJumping && !isVaulting && !isCrouching)
            {
                PerformJump();
            }
        }
    }

    private void UpdateCrouchInput()
    {
        if (Input.GetKeyDown(keyBinds.crouchKey))
        {
            if (isInCombat)
            {
                ExitCombat();
            }

            if (isCrouching)
            {
                isCrouching = false;
                characterAnimator.SetBool(ANIM_CROUCH_MODE, false);
                characterAnimator.SetBool(ANIM_CROUCH_P, false);
                characterAnimator.SetBool(ANIM_CROUCH_S, true);
                crouchSTimer = CROUCH_S_DURATION;
            }
            else
            {
                isCrouching = true;
                characterAnimator.SetBool(ANIM_CROUCH_MODE, true);
            }
        }
    }

    private void UpdateCombatInput()
    {
        if (isVaulting || isJumping || isFalling || attackCooldownTimer > 0) return;

        // Check for non-combat inputs to exit combat
        Vector2 input = GetMovementInput();
        bool isSprinting = Input.GetKey(keyBinds.sprintKey);
        if (isInCombat && (input.magnitude >= 0.1f || isSprinting))
        {
            ExitCombat();
            return;
        }

        // Handle attack input
        if (Input.GetKeyDown(keyBinds.attackKey) && isGrounded)
        {
            isInCombat = true;
            characterAnimator.SetBool(ANIM_COMBAT, true);

            // Reset previous attack animation
            characterAnimator.SetBool(ANIM_RPUNCH, false);
            characterAnimator.SetBool(ANIM_LPUNCH, false);
            characterAnimator.SetBool(ANIM_KICK, false);

            // Trigger current attack and set appropriate cooldown
            switch (currentAttackIndex)
            {
                case 0:
                    characterAnimator.SetBool(ANIM_RPUNCH, true);
                    attackCooldownTimer = RPUNCH_COOLDOWN;
                    break;
                case 1:
                    characterAnimator.SetBool(ANIM_LPUNCH, true);
                    attackCooldownTimer = LPUNCH_COOLDOWN;
                    break;
                case 2:
                    characterAnimator.SetBool(ANIM_KICK, true);
                    attackCooldownTimer = KICK_COOLDOWN;
                    break;
            }

            // Move to next attack in sequence
            currentAttackIndex = (currentAttackIndex + 1) % 3;
        }
    }

    private void ExitCombat()
    {
        isInCombat = false;
        characterAnimator.SetBool(ANIM_COMBAT, false);
        characterAnimator.SetBool(ANIM_RPUNCH, false);
        characterAnimator.SetBool(ANIM_LPUNCH, false);
        characterAnimator.SetBool(ANIM_KICK, false);
        currentAttackIndex = 0; // Reset attack sequence
        attackCooldownTimer = 0f;
    }

    private void PerformJump()
    {
        rb.linearVelocity = new Vector3(rb.linearVelocity.x, 0f, rb.linearVelocity.z);
        rb.AddForce(Vector3.up * settings.jumpForce, ForceMode.Impulse);
        isJumping = true;
        isFalling = false;
        jumpInputBufferTimer = 0f;
        UpdateAnimationStates(false, false, true, false, false);
    }

    private void ProcessMovement()
    {
        Vector2 input = GetMovementInput();
        bool isSprinting = Input.GetKey(keyBinds.sprintKey) && !isCrouching;
        float currentSpeed = isCrouching ? settings.crouchSpeed : (isSprinting ? settings.runSpeed : settings.walkSpeed);

        if (input.magnitude >= 0.1f)
        {
            Vector3 inputDirection = new Vector3(input.x, 0f, input.y).normalized;
            float targetAngle = Mathf.Atan2(inputDirection.x, inputDirection.z) * Mathf.Rad2Deg + cameraTransform.eulerAngles.y;
            moveDirection = Quaternion.Euler(0f, targetAngle, 0f) * Vector3.forward;

            UpdateRotation(targetAngle);
            ApplyMovement(currentSpeed);

            if (isCrouching)
            {
                characterAnimator.SetBool(ANIM_CROUCH_P, true);
            }

            UpdateAnimationStates(!isSprinting && !isCrouching, isSprinting && !isCrouching, isJumping, isFalling, isGrounded);
        }
        else
        {
            moveDirection = Vector3.zero;
            ApplyMovement(0f);
            if (isCrouching)
            {
                characterAnimator.SetBool(ANIM_CROUCH_P, false);
            }
            UpdateAnimationStates(false, false, isJumping, isFalling, isGrounded);
        }
    }

    private void CheckForVault()
    {
        if (isJumping || isFalling || !isGrounded || isCrouching || isInCombat) return;

        Vector3 forwardCheck = transform.forward * col.radius;
        Vector3 startCheck = transform.position + Vector3.up * vaultSettings.minVaultHeight;
        Vector3 endCheck = startCheck + Vector3.up * (vaultSettings.maxVaultHeight - vaultSettings.minVaultHeight);

        if (Physics.CapsuleCast(startCheck, endCheck, col.radius * 0.5f,
            transform.forward, out RaycastHit obstacleHit,
            vaultSettings.vaultCheckDistance, vaultSettings.vaultObstacleMask))
        {
            float obstacleHeight = obstacleHit.point.y - transform.position.y;

            if (obstacleHeight >= vaultSettings.minVaultHeight && obstacleHeight <= vaultSettings.maxVaultHeight)
            {
                Vector3 vaultOverPoint = obstacleHit.point + transform.forward * (col.radius * 2 + obstacleHit.collider.bounds.size.z);
                Vector3 landingCheckStart = vaultOverPoint + Vector3.up * (vaultSettings.maxVaultHeight + 0.1f);

                if (Physics.Raycast(landingCheckStart, Vector3.down, out RaycastHit landingHit,
                    vaultSettings.maxVaultHeight * 2, vaultSettings.vaultLandingMask))
                {
                    if (Vector3.Angle(landingHit.normal, Vector3.up) < 45f)
                    {
                        StartVault(landingHit.point);
                    }
                }
            }
        }
    }

    private void StartVault(Vector3 landingPosition)
    {
        isVaulting = true;
        rb.isKinematic = true;
        vaultStartPosition = transform.position;
        vaultEndPosition = landingPosition;
        vaultLerpTime = 0f;

        float vaultDistance = Vector3.Distance(vaultStartPosition, vaultEndPosition);
        vaultTotalTime = vaultDistance / vaultSettings.vaultSpeed;

        if (characterAnimator != null)
        {
            characterAnimator.ResetTrigger(ANIM_VAULT_TRIGGER);
            characterAnimator.SetTrigger(ANIM_VAULT_TRIGGER);
            characterAnimator.SetBool(ANIM_VAULT, true);
            characterAnimator.SetBool(ANIM_WALK, false);
            characterAnimator.SetBool(ANIM_RUN, false);
            characterAnimator.SetBool(ANIM_JUMP, false);
            characterAnimator.SetBool(ANIM_FALL, false);
            characterAnimator.SetBool(ANIM_CROUCH_MODE, false);
            characterAnimator.SetBool(ANIM_CROUCH_P, false);
            characterAnimator.SetBool(ANIM_COMBAT, false);
            characterAnimator.SetBool(ANIM_RPUNCH, false);
            characterAnimator.SetBool(ANIM_LPUNCH, false);
            characterAnimator.SetBool(ANIM_KICK, false);
        }

        isInCombat = false;
        currentAttackIndex = 0;
    }

    private void ProcessVault()
    {
        vaultLerpTime += Time.fixedDeltaTime;
        float progress = Mathf.Clamp01(vaultLerpTime / vaultTotalTime);

        Vector3 currentPos = Vector3.Lerp(vaultStartPosition, vaultEndPosition, progress);
        float arcHeight = Mathf.Sin(progress * Mathf.PI) * 1.5f;
        currentPos.y += arcHeight;

        rb.MovePosition(currentPos);

        if (progress >= 1f)
        {
            FinishVault();
        }
    }

    private void FinishVault()
    {
        isVaulting = false;
        rb.isKinematic = false;

        if (characterAnimator != null)
        {
            characterAnimator.SetBool(ANIM_VAULT, false);
        }

        UpdateAnimationStates(false, false, false, false, true);
    }

    private Vector2 GetMovementInput()
    {
        return new Vector2(Input.GetAxisRaw("Horizontal"), Input.GetAxisRaw("Vertical"));
    }

    private void UpdateRotation(float targetAngle)
    {
        Quaternion targetRotation = Quaternion.Euler(0f, targetAngle, 0f);
        transform.rotation = Quaternion.Slerp(transform.rotation, targetRotation, settings.rotationSpeed * Time.fixedDeltaTime);
    }

    private void ApplyMovement(float speed)
    {
        Vector3 moveForce = moveDirection * speed;
        Vector3 horizontalVelocity = new Vector3(rb.linearVelocity.x, 0f, rb.linearVelocity.z);
        Vector3 velocityChange = moveForce - horizontalVelocity;
        velocityChange.y = 0;
        rb.AddForce(velocityChange, ForceMode.VelocityChange);
    }

    private void UpdateAnimationStates(bool isWalking, bool isRunning, bool isJumping, bool isFalling, bool isGrounded)
    {
        if (characterAnimator == null || isVaulting) return;

        characterAnimator.SetBool(ANIM_WALK, isWalking && !isJumping && !isFalling && !isInCombat);
        characterAnimator.SetBool(ANIM_RUN, isRunning && !isJumping && !isFalling && !isInCombat);
        characterAnimator.SetBool(ANIM_JUMP, isJumping);
        characterAnimator.SetBool(ANIM_FALL, isFalling);
        characterAnimator.SetBool(ANIM_GROUNDED, isGrounded);
    }

    private void OnDrawGizmosSelected()
    {
        if (col == null) return;

        Gizmos.color = Color.red;
        Vector3 checkPosition = transform.position + col.center - Vector3.up * (col.height / 2 - col.radius);
        Gizmos.DrawWireSphere(checkPosition, col.radius * 0.9f);
        Gizmos.DrawLine(checkPosition, checkPosition + Vector3.down * settings.groundCheckDistance);

        if (!Application.isPlaying) return;

        Gizmos.color = Color.blue;
        Vector3 startCheck = transform.position + Vector3.up * vaultSettings.minVaultHeight;
        Vector3 endCheck = startCheck + Vector3.up * (vaultSettings.maxVaultHeight - vaultSettings.minVaultHeight);
        Gizmos.DrawWireSphere(startCheck, col.radius * 0.5f);
        Gizmos.DrawWireSphere(endCheck, col.radius * 0.5f);
        Gizmos.DrawLine(startCheck, startCheck + transform.forward * vaultSettings.vaultCheckDistance);
        Gizmos.DrawLine(endCheck, endCheck + transform.forward * vaultSettings.vaultCheckDistance);
    }
}

